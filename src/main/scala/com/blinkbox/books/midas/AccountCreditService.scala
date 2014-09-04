package com.blinkbox.books.midas

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.json4s.{DefaultFormats, Formats, FieldSerializer}
import org.json4s.FieldSerializer._
import spray.client.pipelining._
import spray.http.OAuth2BearerToken
import spray.httpx.Json4sJacksonSupport

import scala.concurrent.{ExecutionContext, Future}

/**
 * Interface for the account credit service.
 */
trait AccountCreditService {

  /**
   * Get current credit balance for the user identified by the access token.
   *
   * @param accessToken SSO access token
   * @return a future with a successful result or an Exception. Any exception can be thrown; known exceptions are:
   *         ConnectionException
   *         NotFoundException
   *         UnauthorizedException
   */
  def balance(implicit accessToken: SsoAccessToken): Future[Balance]
}

/**
 * Implementation of account credit service, that uses a Spray client to execute requests.
 */
class DefaultAccountCreditService(config: MidasConfig, client: Client)(implicit ec: ExecutionContext)
  extends AccountCreditService with Json4sJacksonSupport with StrictLogging {

  val balanceSerializer = FieldSerializer[Balance](renameTo("amount", "Balance"), renameFrom("Balance", "amount"))

  override implicit def json4sJacksonFormats: Formats = DefaultFormats + balanceSerializer

  val serviceBase = config.url

  override def balance(implicit accessToken: SsoAccessToken): Future[Balance] = {
    val req = Get(s"$serviceBase/api/wallet/balance")
    client.dataRequest[Balance](req, Some(OAuth2BearerToken(accessToken.value)))
  }
}
