# Redis connection settings
REDIS_HOST = "localhost"
REDIS_PORT = 6379
REDIS_DB = 0

# Image and numpy related parameter
IMAGE_WIDTH = 224
IMAGE_HEIGHT = 224
RESIZE_MIN = 256
IMAGE_CHANS = 3
# Numpy/Mat format, e.g, uint8 or float
IMAGE_DTYPE = "uint8"

# Redis Message Queue parameter
IMAGE_QUEUE = "image_queue"
PREDICT_QUEUE = "predict_queue"
BATCH_SIZE = 4
SERVER_SLEEP = 0.25
CLIENT_SLEEP = 0.25

# Redis Streaming
IMAGE_STREAMING = "image_stream"
# Pub/Sub Topic
IMAGE_TOPIC = "image_topic"
BLOCK = 1000
