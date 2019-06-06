import redis
import settings
import helpers
import cv2
import argparse
import json
import uuid
import time
from os import listdir
from os.path import isfile, join


DB = redis.StrictRedis(host=settings.REDIS_HOST,
                       port=settings.REDIS_PORT, db=settings.REDIS_DB)


def smallest_size_at_least(height, width, resize_min):
    smaller_dim = min(height, width)
    scale_ratio = resize_min / smaller_dim
    new_height = int(height * scale_ratio)
    new_width = int(width * scale_ratio)
    return new_height, new_width


def resize_image(image, height, width):
    return cv2.resize(image, (width, height))


def aspect_preserving_resize(image, resize_min):
    height, width = image.shape[0], image.shape[1]
    new_height, new_width = smallest_size_at_least(height, width, resize_min)
    return resize_image(image, new_height, new_width)


def central_crop(image, crop_height, crop_width):
    height, width = image.shape[0], image.shape[1]
    amount_to_be_cropped_h = (height - crop_height)
    crop_top = amount_to_be_cropped_h // 2
    amount_to_be_cropped_w = (width - crop_width)
    crop_left = amount_to_be_cropped_w // 2
    return image[crop_top:crop_top + crop_height, crop_left:crop_left + crop_width]


def preprocess(path, output_width, output_height):
    image = cv2.imread(path, cv2.IMREAD_COLOR)
    image = aspect_preserving_resize(image, settings.RESIZE_MIN)
    image = central_crop(image, output_height, output_width)
    return image


def image_enqueue(image_path):
    start_time = time.time()
    image = preprocess(image_path, settings.IMAGE_WIDTH,
                       settings.IMAGE_HEIGHT)
    print("Pre-processing %d ms" % int(round((time.time() - start_time) * 1000)))
    # NHWC -> NCWH
    image = image.transpose(2, 0, 1)
    # ensure our NumPy array is C-contiguous as well,
    # otherwise we won't be able to serialize it
    image = image.copy(order="C")

    # generate an ID for the classification then add the
    # classification ID + image to the queue
    k = str(uuid.uuid4())
    image = helpers.base64_encode_image(image)
    d = {"id": k, "path": image_path, "image": image}
    DB.rpush(settings.IMAGE_QUEUE, json.dumps(d))
    print("Total %d ms" % int(round((time.time() - start_time) * 1000)))


def images_enqueue(dir_path):
    for f in listdir(dir_path):
        if isfile(join(dir_path, f)):
            image_enqueue(join(dir_path, f))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--img_path', help="Path where the images are stored")
    args = parser.parse_args()
    images_enqueue(args.img_path)
