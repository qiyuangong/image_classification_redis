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
import com.intel.analytics.bigdl.numeric.NumericFloat
import com.intel.analytics.bigdl.transform.vision.image.ImageFeature
import com.intel.analytics.zoo.common.NNContext
import com.intel.analytics.zoo.feature.image.{ImageBytesToMat, ImageCenterCrop, ImageMatToTensor, ImageResize, ImageSet, ImageSetToSample}
import com.intel.analytics.zoo.models.image.imageclassification.{LabelOutput, LabelReader}
import com.intel.analytics.zoo.pipeline.inference.InferenceModel
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.opencv.imgcodecs.Imgcodecs
import scopt.OptionParser


case class RedisParams(model: String = "",
                       weight: String = "",
                       batchSize: Int = 4,
                       isInt8: Boolean = false,
                       topN: Int = 5,
                       redis: String = "localhost:6379")

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
        .text("The path to OpenVINO model")
        .action((v, p) => p.copy(model = v))
        .required()
      opt[String]('w', "weight")
        .text("The path to OpenVINO model weight")
        .action((v, p) => p.copy(weight = v))
      opt[Int]('b', "batchSize")
        .text("Batch size of input data")
        .action((v, p) => p.copy(batchSize = v))
      opt[Int]("topN")
        .text("top N number")
        .action((v, p) => p.copy(topN = v))
      opt[Boolean]("isInt8")
        .text("Is Int8 optimized model?")
        .action((v, p) => p.copy(isInt8 = v))
      opt[String]("redis")
        .text("redis address and port")
        .action((v, p) => p.copy(redis = v))
    }

    parser.parse(args, RedisParams()).foreach { param =>
      val conf = NNContext.createSparkConf()
        .set("spark.redis.host", param.redis.split(":").head)
        .set("spark.redis.port", param.redis.split(":").last)
      val sc = NNContext.initNNContext(conf,
        "Redis Spark Streaming Test")

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
        .config(sc.getConf)
        .getOrCreate()

      val images = spark
        .readStream
        .format("redis")
        .option("stream.keys", "image_stream")
        .option("stream.read.batch.size", batchSize)
        .schema(StructType(Array(
        StructField("id", StringType),
        StructField("path", StringType),
        StructField("image", StringType)
      )))
        .load()

      val query = images
        .writeStream
        .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
          val batchImage = batchDF.collect().map { image =>
            val bytes = java.util
              .Base64.getDecoder.decode(image.getAs[String]("image"))
            val path = image.getAs[String]("path")
            logger.info(s"image: $path")
            ImageFeature.apply(bytes, null, path)
          }
          val imageSet = ImageSet.array(batchImage)
          val inputs = imageSet ->
            ImageBytesToMat(imageCodec = Imgcodecs.CV_LOAD_IMAGE_COLOR) ->
            ImageResize(256, 256) ->
            ImageCenterCrop(224, 224) ->
            ImageMatToTensor(shareBuffer = false) ->
            ImageSetToSample()
          val batched = inputs.toDataSet() -> SampleToMiniBatch(param.batchSize)
          val start = System.nanoTime()
          val predicts = batched.toLocal()
            .data(false).flatMap { miniBatch =>
            val predict = if (param.isInt8) {
              model.doPredictInt8(miniBatch
                .getInput.toTensor.addSingletonDimension())
            } else {
              model.doPredict(miniBatch
                .getInput.toTensor.addSingletonDimension())
            }
            predict.toTensor.squeeze.split(1).asInstanceOf[Array[Activity]]
          }
          // Add prediction into imageset
          imageSet.array.zip(predicts.toIterable).foreach(tuple => {
            tuple._1(ImageFeature.predict) = tuple._2
          })
          // Transform prediction into Labels and probs
          val labelOutput = LabelOutput(LabelReader.apply("IMAGENET"))
          val results = labelOutput(imageSet).toLocal().array

          // Output results
          results.foreach(imageFeature => {
            logger.info(s"image: ${imageFeature.uri}, top ${param.topN}")
            val classes = imageFeature("classes").asInstanceOf[Array[String]]
            val probs = imageFeature("probs").asInstanceOf[Array[Float]]
            for (i <- 0 until param.topN) {
              logger.info(s"\t class: ${classes(i)}, credit: ${probs(i)}")
            }
          })

          val latency = System.nanoTime() - start
          logger.info(s"Predict latency is ${latency / 1e6} ms")
        }.start()
      query.awaitTermination()
    }
  }
}
