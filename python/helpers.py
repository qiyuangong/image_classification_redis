import numpy as np
import base64
import sys


def base64_encode_image(image_array):
    # base64 encode the input NumPy array
    return base64.b64encode(image_array).decode("utf-8")


def base64_decode_image(image_array, dtype, shape):
    # if this is Python 3, we need the extra step of encoding the
    # serialized NumPy string as a byte object
    if sys.version_info.major == 3:
        image_array = bytes(image_array, encoding="utf-8")

    # convert the string to a NumPy array using the supplied data
    # type and target shape
    image_array = np.frombuffer(base64.decodestring(image_array), dtype=dtype)
    image_array = image_array.reshape(shape)

    # return the decoded image
    return image_array
