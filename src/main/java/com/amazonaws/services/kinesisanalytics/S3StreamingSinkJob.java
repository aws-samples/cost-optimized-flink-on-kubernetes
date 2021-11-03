package com.amazonaws.services.kinesisanalytics;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.util.Collector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;

import java.util.Properties;

public class S3StreamingSinkJob {
    private static String region = null;
    private static String inputStreamName = null;
    private static String s3SinkPath = null;
    private static String checkpointDir = null;
    
    private static DataStream<String> createSourceFromStaticConfig(StreamExecutionEnvironment env) {

        Properties inputProperties = new Properties();
        inputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, region);
        inputProperties.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION,
                "LATEST");
        return env.addSource(new FlinkKinesisConsumer<>(inputStreamName,
                new SimpleStringSchema(),
                inputProperties));
    }

    private static StreamingFileSink<String> createS3SinkFromStaticConfig() {

        final StreamingFileSink<String> sink = StreamingFileSink
                .forRowFormat(new Path(s3SinkPath), new SimpleStringEncoder<String>("UTF-8"))
                .withBucketAssigner(new DateTimeBucketAssigner("yyyy-MM-dd--HH"))
                .build();
        return sink;
    }

    public static void main(String[] args) throws Exception {
        
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool params = ParameterTool.fromArgs(args);
        env.enableCheckpointing(10000); // Every 10 sec
        checkpointDir = params.get("checkpoint-dir");
        region = params.get("region");
        s3SinkPath = params.get("s3SinkPath"); 
        inputStreamName = params.get("inputStreamName"); 
        env.setStateBackend(new HashMapStateBackend());
        env.getCheckpointConfig().setCheckpointStorage(checkpointDir);
        env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);
        
        DataStream<String> input = createSourceFromStaticConfig(env);

        ObjectMapper jsonParser = new ObjectMapper();

        input.map(value -> {
            JsonNode jsonNode = jsonParser.readValue(value, JsonNode.class);
            return new Tuple2<>(jsonNode.get("TICKER").asText(), jsonNode.get("PRICE").asDouble());
        }).returns(Types.TUPLE(Types.STRING, Types.DOUBLE))
                .keyBy(0) // Logically partition the stream per stock symbol
                .timeWindow(Time.seconds(10), Time.seconds(5))  // Sliding window definition
                .max(1) // Calculate mamximum price per stock over the window
                .setParallelism(8) // Set parallelism for the min operator
                .map(value -> value.f0 + "," + value.f1 + "," + value.f1.toString() + "\n")
                .addSink(createS3SinkFromStaticConfig()).name("S3_sink");
        env.execute("Flink S3 Streaming Sink Job");
    }
}