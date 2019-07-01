from stress_test import *
import time

ITERATION = 10


def push_to_redis_stream(image_dicts):
    for image in image_dicts:
        start_time = time.time()
        DB.xadd(settings.IMAGE_STREAMING, image)
        print("* Push to Redis %d ms" % int(round((time.time() - start_time) * 1000)))


def stress_test_stream(image_dicts):
    for _ in range(ITERATION):
        for i in range(0, NUM_REQUESTS):
            # start a new thread to call the API
            t = Thread(target=push_to_redis_stream, args=(image_dicts,))
            t.daemon = True
            t.start()
            time.sleep(SLEEP_COUNT)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--img_path', help="Path where the images are stored")
    args = parser.parse_args()

    images = prepare_images(args.img_path)
    stress_test_stream(images)

    time.sleep(3)
