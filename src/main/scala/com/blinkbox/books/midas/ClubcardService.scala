package com.blinkbox.books.midas

import com.google.common.base.CaseFormat
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.json4s.JsonAST.JField
import org.json4s._
import spray.http.OAuth2BearerToken
import spray.httpx.Json4sJacksonSupport
import spray.httpx.RequestBuilding.Get

import scala.concurrent.{ExecutionContext, Future}

trait ClubcardService {
  def addClubcard(card: Clubcard)(token: SsoAccessToken): Future[Unit]
  def deleteClubcard(number: ClubcardNumber)(token: SsoAccessToken): Future[Unit]
  def clubcardDetails(number: ClubcardNumber)(token: SsoAccessToken): Future[Clubcard]
  def primaryClubcard(token: SsoAccessToken): Future[Clubcard]
  def makePrimary(card: Clubcard)(token: SsoAccessToken): Future[Unit]
  def listClubcards(token: SsoAccessToken): Future[List[Clubcard]]
}

class DefaultClubcardService(config: MidasConfig, client: Client)(implicit ec: ExecutionContext) extends ClubcardService
  with Json4sJacksonSupport with StrictLogging {

  import com.blinkbox.books.midas.DefaultClubcardService._

  override implicit def json4sJacksonFormats: Formats = DefaultFormats + pascalToCamelConverter

  val serviceBase = config.url

  override def addClubcard(card: Clubcard)(token: SsoAccessToken): Future[Unit] = ???

  override def deleteClubcard(number: ClubcardNumber)(token: SsoAccessToken): Future[Unit] = ???

  override def makePrimary(card: Clubcard)(token: SsoAccessToken): Future[Unit] = ???

  override def listClubcards(token: SsoAccessToken): Future[List[Clubcard]] = ???

  override def clubcardDetails(number: ClubcardNumber)(token: SsoAccessToken): Future[Clubcard] = {
    val req = Get(s"$serviceBase/api/wallet/clubcards/${number.value}")
    client.dataRequest[Clubcard](req, Some(OAuth2BearerToken(token.value)))
  }

  override def primaryClubcard(token: SsoAccessToken): Future[Clubcard] = ???
}

object DefaultClubcardService {
  private val pascalToCamel = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_CAMEL).convert _
  private val camelToPascal = CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.UPPER_CAMEL).convert _

  val pascalToCamelConverter = FieldSerializer[AnyRef](
    deserializer = { case JField(name, v) => JField(pascalToCamel(name), v) },
    serializer = { case (name, v) => Some((camelToPascal(name), v)) }
  )
}
