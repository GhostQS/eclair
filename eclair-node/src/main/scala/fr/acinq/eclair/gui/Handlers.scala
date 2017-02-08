package fr.acinq.eclair.gui


import java.awt.TrayIcon.MessageType
import java.awt.{SystemTray, TrayIcon}
import java.io.{File, FileWriter}
import java.time.{LocalDate, LocalDateTime}
import java.time.format.{DateTimeFormatter, FormatStyle}
import javafx.application.Platform
import javafx.scene.control.TextArea

import akka.pattern.ask
import fr.acinq.bitcoin.{BinaryData, MilliSatoshi, Satoshi}
import fr.acinq.eclair._
import fr.acinq.eclair.io.Client
import fr.acinq.eclair.payment.CreatePayment
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}

/**
  * Created by PM on 16/08/2016.
  */
class Handlers(setup: Setup, trayIcon: TrayIcon) extends Logging {

  import setup._

  def open(hostPort: String, fundingSatoshis: Satoshi, pushMsat: MilliSatoshi) = {
    val regex = "([a-fA-F0-9]+)@([a-zA-Z0-9\\.\\-_]+):([0-9]+)".r
    hostPort match {
      case regex(pubkey, host, port) =>
        logger.info(s"connecting to $host:$port")
        system.actorOf(Client.props(host, port.toInt, pubkey, fundingSatoshis, pushMsat, register))
      case _ => {}
    }
  }

  def send(nodeId: String, rhash: String, amountMsat: String) = {
    logger.info(s"sending $amountMsat to $rhash @ $nodeId")
    paymentInitiator ! CreatePayment(amountMsat.toLong, BinaryData(rhash), BinaryData(nodeId))
  }

  def getH(textField: TextField): Unit = {
    import akka.pattern.ask
    (paymentHandler ? 'genh).mapTo[BinaryData].map { h =>
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          textField.setText(h.toString())
        }
      })
    }
  }

  def getPaymentRequest(amountMsat: Long, textField: TextArea): Unit = {
    import akka.pattern.ask
    (paymentHandler ? 'genh).mapTo[BinaryData].map { h =>
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          textField.setText(s"${Globals.Node.id}:$amountMsat:${h.toString()}")
        }
      })
    }
  }

  def exportToDot(file: File): Unit = {
    import akka.pattern.ask
    (router ? 'dot).mapTo[String].map(dot => printToFile(file)(writer => writer.write(dot)))
  }

  def printToFile(f: java.io.File)(op: java.io.FileWriter => Unit) {
    val p = new FileWriter(f)
    try {
      op(p)
    } finally {
      p.close()
    }
  }

  /**
    * Displays a system notification if the system supports it.
    *
    * @param title Title of the notification
    * @param message content of the notification. Accepts line break
    * @param messageType type of the message, default to NONE
    * @param showAppName true if you want the notification title to be preceded by "Eclair - ". True by default
    */
  def notification (title: String, message: String, messageType: TrayIcon.MessageType = MessageType.NONE, showAppName: Boolean = true) = {
    if (SystemTray.isSupported) {
      val smartTitle = if (showAppName) s"Eclair - $title" else title
      trayIcon.displayMessage(smartTitle, message, messageType)
    }
  }
}
