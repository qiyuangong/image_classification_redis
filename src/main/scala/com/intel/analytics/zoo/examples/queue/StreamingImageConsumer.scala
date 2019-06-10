/*
 * Copyright 2018 Analytics Zoo Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.analytics.zoo.examples.queue

import com.intel.analytics.bigdl.dataset.SampleToMiniBatch
import com.intel.analytics.bigdl.nn.abstractnn.Activity
import com.intel.analytics.bigdl.utils.Engine
import com.intel.analytics.bigdl.numeric.NumericFloat
import com.intel.analytics.zoo.common.NNContext
import com.intel.analytics.zoo.feature.image.{ImageCenterCrop, ImageMatToTensor, ImageResize, ImageSet, ImageSetToSample}
import com.intel.analytics.zoo.pipeline.inference.InferenceModel
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import scopt.OptionParser


case class RedisParams(model: String = "",
                       weight: String = "",
                       batchSize: Int = 4,
                       isInt8: Boolean = false)

object StreamingImageConsumer {

  Logger.getLogger("org").setLevel(Level.ERROR)
  Logger.getLogger("akka").setLevel(Level.ERROR)
  Logger.getLogger("breeze").setLevel(Level.ERROR)
  Logger.getLogger("com.intel.analytics.zoo.feature.image").setLevel(Level.ERROR)
  Logger.getLogger("com.intel.analytics.zoo").setLevel(Level.INFO)

  val logger: Logger = Logger.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val parser = new OptionParser[RedisParams]("Redis Streaming Test") {
      opt[String]('m', "model")
        .text("The path to the int8 quantized ResNet50 model snapshot")
        .action((v, p) => p.copy(model = v))
        .required()
      opt[String]('w', "weight")
        .text("The path to the int8 ResNet50 model weight")
        .action((v, p) => p.copy(weight = v))
      opt[Int]('b', "batchSize")
        .text("Batch size of input data")
        .action((v, p) => p.copy(batchSize = v))
      opt[Boolean]("isInt8")
        .text("Is Int8 optimized model?")
        .action((v, p) => p.copy(isInt8 = v))
    }

    parser.parse(args, RedisParams()).map { param =>
      val sc = NNContext.initNNContext("Redis Streaming Test")

      val batchSize = param.batchSize
      val model = new InferenceModel(1)

      if (param.isInt8) {
        model.doLoadOpenVINOInt8(param.model, param.weight, param.batchSize)
      } else {
        model.doLoadOpenVINO(param.model, param.weight)
      }

      // Spark Structured Streaming
      val spark = SparkSession
        .builder
        .master("local[*]")
        .config("spark.redis.host", "localhost")
        .config("spark.redis.port", "6379")
        .getOrCreate()

      val images = spark
        .readStream
        .format("redis")
        .option("stream.keys", "image_stream")
        .option("stream.read.batch.size", batchSize.toString)
        .schema(StructType(Array(
        StructField("id", StringType),
        StructField("path", StringType),
        StructField("image", StringType)
      )))
        .load()

      val predictStart = System.nanoTime()
      var averageLatency = 0L
      val query = images
        .writeStream
        .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
          val batchImage = batchDF.select("image").collect.map { image =>
            java.util.Base64.getDecoder.decode(image.get(0).asInstanceOf[String])
          }
          val images = ImageSet.array(batchImage)
          val inputs = images ->
            ImageResize(256, 256) ->
            ImageCenterCrop(224, 224) ->
            ImageMatToTensor(shareBuffer = false) ->
            ImageSetToSample()
          val batched = inputs.toDataSet() -> SampleToMiniBatch(param.batchSize)
          val start = System.nanoTime()
          val predicts = batched.toLocal()
            .data(false).flatMap {miniBatch =>
            val predict = if (param.isInt8) {
              model.doPredictInt8(miniBatch
                .getInput.toTensor.addSingletonDimension())
            } else {
              model.doPredict(miniBatch
                .getInput.toTensor.addSingletonDimension())
            }
            predict.toTensor.squeeze.split(1).asInstanceOf[Array[Activity]]
          }
          val latency = System.nanoTime() - start
          averageLatency += latency
          logger.info(s"Predict latency is ${latency / 1e6} ms")
        }.start()

      query.awaitTermination()
    }
  }
}
