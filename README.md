# Image Classification with Redis
An Image Classification example based on [Redis](https://redis.io/) and [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo). Redis serves as [Message Broker/Queue](https://en.wikipedia.org/wiki/Message_broker) in this example, such that we can scale-up with multiple producers and multiple consumers. Note that Redis can be replace with RabbitMQ, Kafka or MQ etc. 
 
We prepare 2 examples with Scala and Python implementations.

1. [Python](https://github.com/qiyuangong/image_classification_redis/tree/master/python)
2. [Scala](https://github.com/qiyuangong/image_classification_redis/tree/master/src/main/scala/com/intel/analytics/zoo/examples/queue)

**Basic Requirements:**

1. [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo)
2. [Redis](https://redis.io/). You can obtain Redis from Docker, such that you don't have to install it in your test env, e.g, `docker run --name test-redis -p 6379:6379 -d redis`
3. Pre-trained ResNet-50 model in OpenVINO format. You can get it from [Zoo OpenVINO example](https://github.com/intel-analytics/analytics-zoo/tree/master/zoo/src/main/scala/com/intel/analytics/zoo/examples/vnni/openvino) or [converting a TensorFlow Model with OpenVINO](https://docs.openvinotoolkit.org/latest/_docs_MO_DG_prepare_model_convert_model_Convert_Model_From_TensorFlow.html). Note that Zoo and Tensorflow models are also supported. You can load these models with Zoo after a few modifications.
4. Several test images in JPEG format.

# Basic Example

**Basic Roles:**

1. Message Queue: Redis.
2. Image Producer: Push images into Redis
3. Image Consumer: Pop images from Redis, make prediction. Then, if necessary push results into Redis.

# Pub/Sub Example

**Basic Roles:**

1. Message Queue: Redis.
2. Image Pub: Publish images topic into Redis
3. Image Sub: Subscribe topic that contains images from Redis, make prediction. Then, if necessary push results into Redis.

# Streaming Example

**Additional Requirements:**

1. [Spark 2.4.3](https://spark.apache.org/releases/spark-release-2-4-3.html)
2. [spark-redis](https://github.com/RedisLabs/spark-redis) add to CLASSPATH or `pom.xml`.
3. Redis 5.0+ with [Redis Streams](https://redis.io/topics/streams-intro)

**Basic Roles:**

1. Streaming Queue: Redis Streams
2. Image Producer: Push images into Redis
3. Streaming Image Consumer: Pop images from Redis, make prediction. Then, if necessary push results into Redis.

# Reference
1. [Deep learning in production with Keras, Redis, Flask, and Apache](https://www.pyimagesearch.com/2018/02/05/deep-learning-production-keras-redis-flask-apache/)
2. [Message Queue](https://en.wikipedia.org/wiki/Message_queue)
3. [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo)
4. [Redis](https://redis.io/)
5. [Redis Pub/Sub](https://redis.io/topics/pubsub)
6. [Redis Streams](https://redis.io/topics/streams-intro)
7. [Spark](https://spark.apache.org/)
8. [Spark Structured Streaming](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html)
9. [OpenVINO](https://software.intel.com/en-us/openvino-toolkit)
