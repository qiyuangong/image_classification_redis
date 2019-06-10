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

import com.intel.analytics.bigdl.utils.Engine
import com.intel.analytics.zoo.pipeline.inference.InferenceModel
import org.apache.log4j.Logger
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import scopt.OptionParser


case class ResNet50PerfParams(model: String = "",
                              weight: String = "",
                              batchSize: Int = 4,
                              isInt8: Boolean = false)

object StreamingImageConsumer {

  val logger: Logger = Logger.getLogger(getClass)

  def main(args: Array[String]): Unit = {

    val parser = new OptionParser[ResNet50PerfParams]("ResNet50 Int8 Performance Test") {
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
    }

    parser.parse(args, ResNet50PerfParams()).foreach { param =>

      val batchSize = param.batchSize
      val model = new InferenceModel(1)

      // Init Zoo NNContext
      Engine.init

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

      val shape = Array(2254, 224, 3)

      val predictStart = System.nanoTime()
      var averageLatency = 0L

      val query = images
        .writeStream
        .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
          //val batchImage = batchDF.select("image").collect.map { image =>
          //  val bytes = java.util.Base64.getDecoder.decode(image.asInstanceOf[String])
          //  Tensor.apply(bytes.map(x => x.toInt))
          //}

          val start = System.nanoTime()
          if (param.isInt8) {
            model.doPredictInt8(batchImage)
          } else {
            model.doPredict(batchImage)
          }
          val latency = System.nanoTime() - start
          averageLatency += latency
          logger.info(s"Predict latency is ${latency / 1e6} ms")
        }.start()

      query.awaitTermination()
    }
  }
}
