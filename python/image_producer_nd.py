import redis
import settings
import helpers
import argparse
import json
import uuid
import time
import cv2
from os import listdir
from os.path import isfile, join


DB = redis.StrictRedis(host=settings.REDIS_HOST,
                       port=settings.REDIS_PORT, db=settings.REDIS_DB)


def image_enqueue(image_path):
    # Imaging you already have a image MAT
    start_time = time.time()
    # ND array
    image = cv2.imread(image_path, cv2.IMREAD_COLOR)
    # Use Png rather than Jpeg, jpeg is lossy compression
    image = cv2.imencode(".png", image)[1]
    # generate an ID for the classification then add the
    # classification ID + image to the queue
    k = str(uuid.uuid4())
    image = helpers.base64_encode_image(image)
    d = {"id": k, "path": image_path, "image": image}
    DB.rpush(settings.IMAGE_QUEUE, json.dumps(d))
    print("Push to redis %d ms" % int(round((time.time() - start_time) * 1000)))


def images_enqueue(dir_path):
    for f in listdir(dir_path):
        if isfile(join(dir_path, f)):
            image_enqueue(join(dir_path, f))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--img_path', help="Path where the images are stored")
    args = parser.parse_args()
    images_enqueue(args.img_path)
