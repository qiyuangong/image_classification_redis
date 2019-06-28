import numpy as np
from zoo.pipeline.inference import InferenceModel
import settings
import helpers
import redis
import json
import argparse
import uuid
from pyspark.sql import SparkSession
from pyspark.sql.types import StructType

queue = redis.StrictRedis(host=settings.REDIS_HOST,
                       port=settings.REDIS_PORT, db=settings.REDIS_DB)
p = queue.pubsub()
p.subscribe('channel_1','channel_2')


def classify_process(stream_batch, batch_id):
    image_ids = []
    batch = None
    # loop over the queue
    for q in stream_batch:
        # deserialize the object and obtain the input image
        image = helpers.base64_decode_image(q['image'],settings.IMAGE_DTYPE)
        # check to see if the batch list is None
        if batch is None:
            batch = image
        # otherwise, stack the data
        else:
            batch = np.vstack([batch, image])

        # update the list of image IDs
        image_ids.append(q["id"])

    # check to see if we need to process the batch
    if len(image_ids) > 0:
        # classify the batch
        batch = np.expand_dims(batch, axis=0)
        print("* Batch size: {}".format(batch.shape))
        # Output is [1, 4, 1000]
        results = model.predict(batch)[0]

        # loop over the image IDs and their corresponding set of
        # results from our model
        for (image_id, resultSet) in zip(image_ids, results):
            # initialize the list of output predictions
            output = {}
            # loop over the results and add them to the list of
            # output predictions
            # Top 1
            max_index = np.argmax(resultSet)
            output["Top-1"] = str(max_index)
            output["id"] = image_id
            print("* Predict result " + str(output))
            # store the output predictions in the database, using
            # the image ID as the key so we can fetch the results
            queue.lpush(settings.PREDICT_QUEUE, json.dumps(output))

        # remove the set of images from our queue
        queue.ltrim(settings.IMAGE_QUEUE, len(image_ids), -1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--model_path', help="Zoo model path")
    args = parser.parse_args()
    model_path = args.model_path

    print("* Loading model...")
    model = InferenceModel()
    model.load_openvino(model_path=model_path,
                        weight_path=model_path[:model_path.rindex(".")] + ".bin")
    print("* Model loaded")

    b=0
    bid=0
    stream_batch=[]
    for item in p.listen():
        if item['type'] == 'message':
            print("get message")           
            k = str(uuid.uuid4())
            d = {"id": str(k), "image": item['data']}
            stream_batch.append(d)
            b=b+1
            if b==settings.BATCH_SIZE:
                bid=bid+1
                print("start process")
                print(len(stream_batch[-4:]))
                query = classify_process(stream_batch[-4:],bid)
                b=0
    query.awaitTermination()
