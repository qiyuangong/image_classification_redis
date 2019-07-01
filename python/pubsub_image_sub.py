import numpy as np
from zoo.pipeline.inference import InferenceModel

import settings
import helpers
import redis
import time
import json
import argparse

DB = redis.StrictRedis(host=settings.REDIS_HOST,
                       port=settings.REDIS_PORT, db=settings.REDIS_DB)
pub = DB.pubsub()
pub.subscribe('channel')


def classify_process(model_path):
    print("* Loading model...")
    model = InferenceModel()
    model.load_openvino(model_path=model_path,
                        weight_path=model_path[:model_path.rindex(".")] + ".bin")
    print("* Model loaded")
    # continually poll for new images to classify
    while True:
        image_ids = []
        batch = None
        # loop over the queue
        start_time = time.time()
        count = 0
        while count < 4:
            # Get message or None or timeout
            record = pub.get_message()
            if record and record['type'] == 'message':
                data = json.loads(record['data'].decode("utf-8"))
                image = helpers.base64_decode_image(data["image"])
                image = helpers.byte_to_mat(image, dtype=settings.IMAGE_DTYPE)
                image = helpers.image_preprocess(image, settings.IMAGE_WIDTH, settings.IMAGE_HEIGHT)
                # check to see if the batch list is None
                if batch is None:
                    batch = image
                # otherwise, stack the data
                else:
                    batch = np.vstack([batch, image])
                # update the list of image IDs
                image_ids.append(data["id"])
                count += 1
        print("* Pop from redis %d ms" % int(round((time.time() - start_time) * 1000)))
        # check to see if we need to process the batch
        if len(image_ids) > 0:
            # classify the batch
            batch = np.expand_dims(batch, axis=0)
            print("* Batch size: {}".format(batch.shape))
            # Output is [1, 4, 1000]
            results = model.predict(batch)[0]
            print("* Predict a batch %d ms" % int(round((time.time() - start_time) * 1000)))
            # loop over the image IDs and their corresponding set of
            # results from our model
            for (imageID, resultSet) in zip(image_ids, results):
                # initialize the list of output predictions
                output = {}
                # loop over the results and add them to the list of
                # output predictions
                # Top 1
                max_index = np.argmax(resultSet)
                output["Top-1"] = str(max_index)
                output["id"] = imageID
                print("* Predict result " + str(output))
                # store the output predictions in the database, using
                # the image ID as the key so we can fetch the results
                DB.lpush(settings.PREDICT_QUEUE, json.dumps(output))

            # remove the set of images from our queue
            print("* Total time used is %d ms" % int(round((time.time() - start_time) * 1000)))

        # sleep for a small amount
        time.sleep(settings.SERVER_SLEEP)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--model_path', help="Zoo model path")
    args = parser.parse_args()
    classify_process(args.model_path)
