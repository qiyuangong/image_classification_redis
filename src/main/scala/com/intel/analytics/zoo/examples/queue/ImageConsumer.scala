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
import com.intel.analytics.bigdl.transform.vision.image.ImageFeature
import com.intel.analytics.bigdl.utils.Engine
import com.intel.analytics.bigdl.numeric.NumericFloat
import com.intel.analytics.zoo.feature.image._
import com.intel.analytics.zoo.models.image.imageclassification.{LabelOutput, LabelReader}
import com.intel.analytics.zoo.pipeline.inference.InferenceModel
import org.apache.log4j.{Level, Logger}
import org.opencv.imgcodecs.Imgcodecs
import redis.clients.jedis.Jedis
import scopt.OptionParser

import scala.util.parsing.json.JSON


object ImageConsumer {

  Logger.getLogger("org").setLevel(Level.ERROR)
  Logger.getLogger("akka").setLevel(Level.ERROR)
  Logger.getLogger("breeze").setLevel(Level.ERROR)
  Logger.getLogger("com.intel.analytics.zoo.feature.image").setLevel(Level.ERROR)
  Logger.getLogger("com.intel.analytics.zoo").setLevel(Level.INFO)

  val logger: Logger = Logger.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    // Enable MKLDNN local mode
    System.setProperty("bigdl.localMode", "true")
    System.setProperty("bigdl.engineType", "mkldnn")

    val parser = new OptionParser[RedisParams]("ResNet50 Int8 Performance Test") {
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
        .action((x, c) => c.copy(isInt8 = x))
      opt[String]("redis")
        .text("redis address and port")
        .action((v, p) => p.copy(redis = v))
    }

    parser.parse(args, RedisParams()).foreach { param =>
      val batchSize = param.batchSize

      // Redis Connection
      // Default is localhost
      val redisDB = new Jedis(param.redis)

      Engine.init

      val model = new InferenceModel(1)

      if (param.isInt8) {
        model.doLoadOpenVINOInt8(param.model, param.weight, param.batchSize)
      } else {
        model.doLoadOpenVINO(param.model, param.weight)
      }

      while (true) {
        // Get batch from queue
        val batch = redisDB.lrange("image_queue", 0 , batchSize - 1)
        val batchImage = List.range(0, batch.size()).toArray.map { i =>
          val json = JSON.parseFull(batch.get(i))
          json match {
            case Some(map: Map[String, Any]) =>
              val path = map("path").asInstanceOf[String]
              val image = map("image").asInstanceOf[String]
              val bytes = java.util
                .Base64.getDecoder.decode(image)
              ImageFeature.apply(bytes, null, path)
            case _ => throw new Exception("Parse json failed.")
          }
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
        // Equal to Lpop, remove from queue
        redisDB.ltrim("image_queue", batch.size(), -1)
      }
    }
  }
}
