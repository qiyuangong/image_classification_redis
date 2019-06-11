from stress_test import *


def push_to_redis_stream(image_dicts):
    for image in image_dicts:
        DB.xadd(settings.IMAGE_STREAMING, image)


def stress_stream_test(image_dicts):
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
    stress_stream_test(images)

    time.sleep(3)
