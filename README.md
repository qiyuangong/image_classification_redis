# Image Classification with Redis
An Image Classification example based on [Redis](https://redis.io/) and [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo).
 

# Requirements

1. [Analytics-Zoo](https://github.com/intel-analytics/analytics-zoo)
2. [Redis](https://redis.io/)
3. Spark
4. Python


Basic Roles:

1. Message Queue: Redis
2. Image Producer: Push images into Redis
3. Image Consumer: Pop images from Redis, make prediction. +Then, push results into Redis.


# Reference
[Deep learning in production with Keras, Redis, Flask, and Apache
](https://www.pyimagesearch.com/2018/02/05/deep-learning-production-keras-redis-flask-apache/)
