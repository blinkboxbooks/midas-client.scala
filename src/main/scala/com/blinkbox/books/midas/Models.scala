package com.blinkbox.books.midas

case class Balance(amount: BigDecimal)
case class Clubcard(cardNumber: String, displayName: String, isPrimaryCard: Boolean, isPrivilegeCard: Boolean)
case class AddClubcardRequest(cardNumber: String, displayName: String, isPrimaryCard: Boolean)
case class SsoAccessToken(value: String)
