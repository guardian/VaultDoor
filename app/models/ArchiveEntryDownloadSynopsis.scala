package models


case class ArchiveEntryDownloadSynopsis(entryId:String, path:String, fileSize:Long)

/*
this model is lifted directly from ArchiveHunter, it is the data format that Download Manager expects to receive.
 */

object ArchiveEntryDownloadSynopsis extends ((String, String, Long)=>ArchiveEntryDownloadSynopsis) {

}