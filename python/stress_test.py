from threading import Thread
from image_producer import *

# initialize the number of requests for the stress test along with
# the sleep amount between requests
NUM_REQUESTS = 500
SLEEP_COUNT = 0.05


def prepare_images(img_path):
    image_dicts = []
    for f in listdir(img_path):
        if isfile(join(img_path, f)):
            image_path = join(img_path, f)
            with open(image_path, "rb") as imageFile:
                # generate an ID for the classification then add the
                # classification ID + image to the queue
                k = str(uuid.uuid4())
                image = helpers.base64_encode_image(imageFile.read())
                image_dicts.append({"id": k, "path": image_path, "image": image})
    return image_dicts


def push_to_redis(image_dicts):
    for image in image_dicts:
        DB.rpush(settings.IMAGE_QUEUE, json.dumps(image))


def stress_test(image_dicts):
    for i in range(0, NUM_REQUESTS):
        # start a new thread to call the API
        t = Thread(target=push_to_redis, args=(image_dicts,))
        t.daemon = True
        t.start()
        time.sleep(SLEEP_COUNT)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--img_path', help="Path where the images are stored")
    args = parser.parse_args()

    images = prepare_images(args.img_path)
    stress_test(images)

    time.sleep(3)
