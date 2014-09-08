package com.blinkbox.books.midas

case class Balance(amount: BigDecimal)
case class Clubcard(number: ClubcardNumber, displayName: String, primary: Boolean = false, privileged: Boolean = false)

case class SsoAccessToken(value: String)
case class ClubcardNumber(value: String)
