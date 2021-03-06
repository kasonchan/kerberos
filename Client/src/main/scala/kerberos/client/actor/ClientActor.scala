package kerberos.client.actor

import akka.actor.{Actor, ActorSelection, Props}
import kerberos.encryption.{ElGamal, ElGamalPrivateKey, ElGamalPublicKey}
import kerberos.messages._

/**
 * Created by kasonchan on 1/29/15.
 */
object ClientActor {
  //  Application hostname and port
  def props(aHostname: String, aPort: String): Props =
    Props(new ClientActor(aHostname, aPort))
}

class ClientActor(aHostname: String, aPort: String) extends Actor
with akka.actor.ActorLogging with ElGamal {

  //  Create the remote key server actor
  val keyServerSupervisor: ActorSelection =
    context.actorSelection("akka.tcp://KeyServerSystem@127.0.0.1:2552/user/KeyServerSupervisor")

  val applicationSupervisor: ActorSelection =
    context.actorSelection("akka.tcp://ApplicationSystem@" + aHostname + ":" + aPort + "/user/ApplicationSupervisor")

  //  Client public and private keys
  val publicKey = ElGamalPublicKey(1579, 1571, 677)
  val privateKey = ElGamalPrivateKey(11)

  override def preStart(): Unit = {
    log.info("Pre-start")
  }

  override def postStop(): Unit = {
    log.info("Post-stop")
  }

  def receive = {
    case serviceSessionKeyRequest: ServiceSessionKeyRequest => {
      context.become(active(serviceSessionKeyRequest.service), discardOld = false)

      log.info(sender() + " " + serviceSessionKeyRequest.service.toString)

      self ! serviceSessionKeyRequest
    }
    case x => {
      log.warning("Undefined operation: " + sender() + " " + x.toString)
    }
  }

  def active(service: Service): Receive = {
    case serviceSessionKeyRequest: ServiceSessionKeyRequest => {
      val sessionKeyRequest = SessionKeyRequest(serviceSessionKeyRequest.CID,
        serviceSessionKeyRequest.SID)

      log.info(sender() + " " + sessionKeyRequest.toString)

      keyServerSupervisor ! sessionKeyRequest
    }
    case sessionKeyReply: SessionKeyReply => {
      sessionKeyReply match {
        case SessionKeyReply(cid, sid, sessionKey, encryptedToken) =>
          log.info(sender() + " " + sessionKeyReply.toString)

          val dCID = ElGamal_DecryptMessage(publicKey, privateKey, cid)
          val dSID = ElGamal_DecryptMessage(publicKey, privateKey, sid)

          val dSessionKeyP = ElGamal_Decrypt(publicKey, privateKey, sessionKey.p)
          val dSessionKeyAlpha = ElGamal_Decrypt(publicKey, privateKey, sessionKey.alpha)
          val dSessionKeyAA = ElGamal_Decrypt(publicKey, privateKey, sessionKey.aa)

          val dSessionKey = ElGamalPublicKey(dSessionKeyP, dSessionKeyAlpha, dSessionKeyAA)

          log.info(dCID + " " + dSID + " " + dSessionKey + " " + encryptedToken)

          val decryptedSessionKeyReply = DecryptedSessionKeyReply(dCID, dSID, dSessionKey,
            encryptedToken)

          self ! decryptedSessionKeyReply
      }
    }
    case decryptedSessionKeyReply: DecryptedSessionKeyReply => {
      log.info(sender() + " " + decryptedSessionKeyReply.toString)
      log.info(service.toString)

      context.unbecome()
    }
    case serviceRequest: ServiceRequest => {
      log.info(sender() + " " + serviceRequest.toString)

      applicationSupervisor ! serviceRequest
    }
    case msg: String => {
      log.info(msg)

      msg match {
        case "exit" => context.system.shutdown()
        case "test application" => applicationSupervisor ! msg
        case x => log.warning("Undefined operation: " + sender() + " " + x)
      }
    }
    case x => {
      log.warning("Undefined operation: " + sender() + " " + x.toString)
    }
  }
}
