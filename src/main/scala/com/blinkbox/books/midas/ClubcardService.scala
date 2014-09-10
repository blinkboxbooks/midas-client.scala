package com.blinkbox.books.midas

import com.blinkbox.books.midas.SerializationHelpers.pascalToCamelConverter
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.json4s._
import spray.http.OAuth2BearerToken
import spray.httpx.Json4sJacksonSupport
import spray.httpx.RequestBuilding.{Get, Post}

import scala.concurrent.{ExecutionContext, Future}

trait ClubcardService {
  def addClubcard(number: String, displayName: String, makePrimary: Boolean = false)(implicit token: SsoAccessToken): Future[Clubcard]
  def deleteClubcard(number: String)(implicit token: SsoAccessToken): Future[Unit]
  def clubcardDetails(number: String)(implicit token: SsoAccessToken): Future[Clubcard]
  def primaryClubcard()(implicit token: SsoAccessToken): Future[Clubcard]
  def makePrimary(card: Clubcard)(implicit token: SsoAccessToken): Future[Unit]
  def listClubcards()(implicit token: SsoAccessToken): Future[List[Clubcard]]
}

class DefaultClubcardService(config: MidasConfig, client: Client)(implicit ec: ExecutionContext) extends ClubcardService
  with Json4sJacksonSupport with StrictLogging {

  override implicit def json4sJacksonFormats: Formats = DefaultFormats + pascalToCamelConverter

  val serviceBase = config.url

  override def addClubcard(number: String, displayName: String, makePrimary: Boolean = true)(implicit token: SsoAccessToken): Future[Clubcard] = {
    val reqData = AddClubcardRequest(number, displayName, makePrimary)
    val req = Post(s"$serviceBase/api/wallet/clubcards", reqData)
    client.dataRequest[Clubcard](req, Some(OAuth2BearerToken(token.value)))
  }

  override def deleteClubcard(number: String)(implicit token: SsoAccessToken): Future[Unit] = ???

  override def makePrimary(card: Clubcard)(implicit token: SsoAccessToken): Future[Unit] = ???

  override def listClubcards()(implicit token: SsoAccessToken): Future[List[Clubcard]] = ???

  override def clubcardDetails(number: String)(implicit token: SsoAccessToken): Future[Clubcard] = {
    val req = Get(s"$serviceBase/api/wallet/clubcards/$number")
    client.dataRequest[Clubcard](req, Some(OAuth2BearerToken(token.value)))
  }

  override def primaryClubcard()(implicit token: SsoAccessToken): Future[Clubcard] = {
    val req = Get(s"$serviceBase/api/wallet/clubcards/primary")
    client.dataRequest[Clubcard](req, Some(OAuth2BearerToken(token.value)))
  }
}
