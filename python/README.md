# Python Image Classification with Redis
An Image Classification example based on [Redis](https://redis.io/) and [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo). Redis serves as [Message Broker/Queue](https://en.wikipedia.org/wiki/Message_broker) in this example, such that we can scale-up with multiple producers and multiple consumers.
 
**Basic Requirements:**

1. [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo)
2. [Redis](https://redis.io/). You can obtain Redis from Docker, such that you don't have to install it in your test env, e.g, `docker run --name test-redis -p 6379:6379 -d redis`
3. Pre-trained ResNet-50 model in OpenVINO format. You can get it from [Zoo OpenVINO example](https://github.com/intel-analytics/analytics-zoo/tree/master/zoo/src/main/scala/com/intel/analytics/zoo/examples/vnni/openvino) or [converting a TensorFlow Model with OpenVINO](https://docs.openvinotoolkit.org/latest/_docs_MO_DG_prepare_model_convert_model_Convert_Model_From_TensorFlow.html). Note that Zoo and Tensorflow models are also supported. You can load these models with Zoo after a few modifications.
4. Several test images in JPEG format.
5. Python 3 and `pip install redis bigdl analytics-zoo pyspark`

# Basic Example

**Basic Roles:**

1. Message Queue: Redis.
2. Image Producer (image_producer.py): Push images into Redis
3. Image Consumer (image_consumer.py): Pop images from Redis, make prediction. Then, if necessary push results into Redis.

```bash
python image_consumer.py --model_path=${openvino model path, *.xml}
```

Open another terminal
```bash
python image_producer.py --img_path=${image dir}
```

# Streaming Example

**Additional Requirements:**

1. [Spark 2.4.3](https://spark.apache.org/releases/spark-release-2-4-3.html)
2. [spark-redis](https://github.com/RedisLabs/spark-redis) add to CLASSPATH
3. Redis 5.0+ with [Redis Streams](https://redis.io/topics/streams-intro)

**Basic Roles:**

1. Streaming Queue: Redis Streams
2. Image Producer (streaming_image_producer.py): Push images into Redis
3. Streaming Image Consumer (streaming_image_consumer.py): Pop images from Redis, make prediction. Then, if necessary push results into Redis.

```bash
${SPARK_HOME}/bin/spark-submit --master "local[*]" \
    --driver-memory 5g \
    --jars ${analytics-zoo jar},${spark-redis.jar},target/zoo-image-classification-redis-0.1.0-SNAPSHOT.jar} \
    streaming_image_consumer.py --model_path=${openvino model path, *.xml}
```

Open another terminal
```bash
python streaming_image_producer.py --img_path=${image dir}
```

# Stress Test
Launch multiple threads, and push images into Redis in parallel. Tune parameters in `stress_test.py` and `streaming_stress_test.py`, such that you can evaluate latency and throughput of your application.

**Basic Stress Test:**

Launch Image Consumer.
```bash
python image_consumer.py --model_path=${openvino model path, *.xml}
```

Open another terminal and launch Stress Test.
```bash
python stress_test.py --img_path=${image dir}
```

**Streaming Stress Test:**

Launch Image Consumer.
```bash
${SPARK_HOME}/bin/spark-submit --master "local[*]" \
    --driver-memory 5g \
    --jars ${analytics-zoo jar},${spark-redis.jar},target/zoo-image-classification-redis-0.1.0-SNAPSHOT.jar} \
    streaming_image_consumer.py --model_path=${openvino model path, *.xml}
```

Open another terminal and launch Streaming Stress Test.
```bash
python streaming_stress_test.py --img_path=${image dir}
```

# Configuration
Default configurations, i.e., queue name, batch size and image shape etc, are located in `settings.py`.

