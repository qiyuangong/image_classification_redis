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


def classify_process(model_path):
    # load the pre-trained Keras model (here we are using a model
    # pre-trained on ImageNet and provided by Keras, but you can
    # substitute in your own networks just as easily)
    print("* Loading model...")
    model = InferenceModel()
    model.load(model_path)
    print("* Model loaded")

    # continually pool for new images to classify
    while True:
        # attempt to grab a batch of images from the database, then
        # initialize the image IDs and batch of images themselves
        queue = DB.lrange(settings.IMAGE_QUEUE, 0,
                          settings.BATCH_SIZE - 1)
        imageIDs = []
        batch = None

        # loop over the queue
        for q in queue:
            # deserialize the object and obtain the input image
            q = json.loads(q.decode("utf-8"))
            image = helpers.base64_decode_image(q["image"],
                                                settings.IMAGE_DTYPE,
                                                (1, settings.IMAGE_HEIGHT, settings.IMAGE_WIDTH,
                                                 settings.IMAGE_CHANS))
            # check to see if the batch list is None
            if batch is None:
                batch = image
            # otherwise, stack the data
            else:
                batch = np.vstack([batch, image])

            # update the list of image IDs
            imageIDs.append(q["id"])

        # check to see if we need to process the batch
        if len(imageIDs) > 0:
            # classify the batch
            print("* Batch size: {}".format(batch.shape))
            results = model.predict(batch)

            # loop over the image IDs and their corresponding set of
            # results from our model
            for (imageID, resultSet) in zip(imageIDs, results):
                # initialize the list of output predictions
                output = []
                # loop over the results and add them to the list of
                # output predictions
                for (imagenetID, label, prob) in resultSet:
                    r = {"label": label, "probability": float(prob)}
                    output.append(r)

                # store the output predictions in the database, using
                # the image ID as the key so we can fetch the results
                DB.set(imageID, json.dumps(output))

            # remove the set of images from our queue
            DB.ltrim(settings.IMAGE_QUEUE, len(imageIDs), -1)

        # sleep for a small amount
        time.sleep(settings.SERVER_SLEEP)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--model_path', help="Zoo model path")
    args = parser.parse_args()
    classify_process(args.model_path)
