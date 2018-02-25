/*
 * Copyright 2014 Frédéric Cabestre
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

package net.sigusr.mqtt.examples

import java.net.InetSocketAddress
import java.util.Properties

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import shapeless.record
//import kafka.message.{NoCompressionCodec, SnappyCompressionCodec}
import net.sigusr.mqtt.api._
//import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import org.apache.kafka.clients.producer.ProducerRecord

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord


import scala.util.Random

class LocalSubscriber(topics: Vector[String]) extends Actor {

  val stopTopic: String = s"$localSubscriber/stop"
  val brokers = "ubuntu:9092"////////////////////////////////////////////////////////////////////////
  val props = new Properties()
  props.put("metadata.broker.list", brokers)
  props.put("zk.connect", "ubuntu:2181");/////////////////////////////////////////////////////////
  props.put("serializer.class", "kafka.serializer.StringEncoder")
  props.put("request.required.acks", "1")
  props.put("producer.type", "async")
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
  props.put(ProducerConfig.BATCH_SIZE_CONFIG, "0")
  props.put(ProducerConfig.LINGER_MS_CONFIG, "0")
  props.put(ProducerConfig.ACKS_CONFIG, "1")
  props.put("key.serializer", "org.apache.kafka.common.serialization.IntegerSerializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  //props.put("compression.codec", "1")
  //import org.apache.kafka.clients.producer.KafkaProducer
  val kafkaProducer = new KafkaProducer[Integer, String](props)
  //val kafkaConfig = new ProducerConfig(props)
  //val producer = new Producer[String, String](kafkaConfig)

  context.actorOf(Manager.props(new InetSocketAddress("ubuntu",1883))) ! Connect(localSubscriber)///////////////////////////////////////
  def makeRandomByteVector(size: Int) = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes.to[Vector]
  }
  def receive: Receive = {
    case Connected ⇒
      println("Successfully connected to localhost:1883")
      sender() ! Subscribe((stopTopic +: topics) zip Vector.fill(topics.length + 1) { AtMostOnce }, 1)
      context become ready(sender())

    case ConnectionFailure(reason) ⇒
      println(s"Connection to localhost:1883 failed [$reason]")
  }

  def ready(mqttManager: ActorRef): Receive = {
    case Subscribed(vQoS, MessageId(1)) ⇒
      println("Successfully subscribed to topics:")
      println(topics.mkString(" ", ",\n ", ""))
    case Message(`stopTopic`, _) ⇒
      mqttManager ! Disconnect
      context become disconnecting
    case Message(topic, payload) ⇒
      val message = new String(payload.to[Array], "UTF-8")
      //kafkaProducer.send(new KeyedMessage[String, String](topic, message))
      val record = new ProducerRecord[Integer,String](topic, message)
      kafkaProducer.send(record)
      //kafkaProducer.send(record).get()
      println("------------"+ s"[$topic] $message")
  }

  def disconnecting(): Receive = {
    case Disconnected ⇒
      println("Disconnected from localhost:1883")
      LocalSubscriber.shutdown()
  }
}

object LocalSubscriber {

  val config =
    """akka {
         loglevel = INFO
         actor {
            debug {
              receive = off
              autoreceive = off
              lifecycle = off
            }
         }
       }
    """
  val system = ActorSystem(localSubscriber, ConfigFactory.parseString(config))

  def shutdown(): Unit = {
    system.terminate()
    println(s"<$localSubscriber> stopped")
  }

  def main(args: Array[String]) = {
    system.actorOf(Props(classOf[LocalSubscriber], args.to[Vector]))
    sys.addShutdownHook { shutdown() }
    println(s"<$localSubscriber> started")
  }
}
