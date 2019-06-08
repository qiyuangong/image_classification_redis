# Image Classification with Redis
An Image Classification example based on [Redis](https://redis.io/) and [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo).
 
We prepare 2 examples with Scala and Python implementations.

1. [Python](https://github.com/qiyuangong/image_classification_redis/tree/master/python)
2. [Scala]()


**Basic Requirements:**

1. [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo)
2. [Redis](https://redis.io/). You can obtain Redis from Docker, such that you don't have to install it in your test env, e.g, `docker run --name test-redis -p 6370:6379 -d redis`.
3. Pre-trained ResNet-50 model in OpenVINO format. You can get it from [Zoo OpenVINO example](https://github.com/intel-analytics/analytics-zoo/tree/master/zoo/src/main/scala/com/intel/analytics/zoo/examples/vnni/openvino) or [converting a TensorFlow Model with OpenVINO](https://docs.openvinotoolkit.org/latest/_docs_MO_DG_prepare_model_convert_model_Convert_Model_From_TensorFlow.html)
4. Several test images in JPEG format.


# Basic Example

**Basic Roles:**

1. Message Queue: Redis.
2. Image Producer: Push images into Redis
3. Image Consumer: Pop images from Redis, make prediction. Then, if necessary push results into Redis.

Note that Redis can be replace with RabbitMQ, Kafka or MQ etc. 

# Streaming Example

**Additional Requirements:**

1. Spark 2.4
2. [spark-redis](https://github.com/RedisLabs/spark-redis) add to CLASSPATH.

**Basic Roles:**

1. Streaming Queue: Redis.
2. Image Producer: Push images into Redis
3. Streaming Image Consumer: Pop images from Redis, make prediction. Then, if necessary push results into Redis.

Note that Redis can be replace with RabbitMQ, Kafka or MQ etc.

# Reference
1. [Deep learning in production with Keras, Redis, Flask, and Apache
](https://www.pyimagesearch.com/2018/02/05/deep-learning-production-keras-redis-flask-apache/)
2. [Message Queue](https://en.wikipedia.org/wiki/Message_queue)
3. [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo)
4. [Redis](https://redis.io/)
5. [Redis Streaming](https://redis.io/topics/streams-intro)
6. [Spark](https://spark.apache.org/)
7. [Spark Structured Streaming](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html)
8. [OpenVINO](https://software.intel.com/en-us/openvino-toolkit)
