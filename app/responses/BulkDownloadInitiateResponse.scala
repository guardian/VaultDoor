package responses

import models.{ArchiveEntryDownloadSynopsis, LightboxBulkEntry}

/*
this response is lifted directly from ArchiveHunter, it is the data format that Download Manager expects to receive.
 */
case class BulkDownloadInitiateResponse(status:String, metadata:LightboxBulkEntry, retrievalToken:String, entries:Seq[ArchiveEntryDownloadSynopsis])
