package models

import java.time.ZonedDateTime
import java.util.UUID

/*
this model is lifted directly from ArchiveHunter, it is the data format that Download Manager expects to receive.
 */

case class LightboxBulkEntry (id:String, description:String, userEmail:String, addedAt: ZonedDateTime, errorCount:Int, availCount:Int, restoringCount:Int)

object LightboxBulkEntry extends ((String,String,String,ZonedDateTime,Int,Int,Int)=>LightboxBulkEntry) {
  def create(userEmail:String, description:String, addTime:Option[ZonedDateTime]=None) =
    new LightboxBulkEntry(UUID.randomUUID().toString, description, userEmail, addTime.getOrElse(ZonedDateTime.now()),0,0,0)

  def forLoose(userEmail:String, count:Int) = LightboxBulkEntry("loose","Loose items", userEmail, ZonedDateTime.now(),0,count,0)
}