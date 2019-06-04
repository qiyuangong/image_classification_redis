# initialize Redis connection settings
REDIS_HOST = "localhost"
REDIS_PORT = 6379
REDIS_DB = 0

# initialize constants used to control image spatial dimensions and
# data type
IMAGE_WIDTH = 224
IMAGE_HEIGHT = 224
RESIZE_MIN = 256
IMAGE_CHANS = 3
IMAGE_DTYPE = "uint8"

# initialize constants used for server queuing
IMAGE_QUEUE = "image_queue"
PREDICT_QUEUE = "predict_queue"
BATCH_SIZE = 4
SERVER_SLEEP = 0.25
CLIENT_SLEEP = 0.25