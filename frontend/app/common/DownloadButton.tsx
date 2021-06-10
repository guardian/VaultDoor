import React, {useContext, useEffect, useState} from "react";
import { authenticatedFetch } from "../auth";
import LoadingIndicator from "../LoadingIndicator.jsx";
import SearchComponentContext from "../searchnbrowse/SearchComponentContext";
import {Typography} from "@material-ui/core";

interface DownloadButtonProps {
  oid: string;
  fileName: string;
}

const DownloadButton:React.FC<DownloadButtonProps> = (props) => {
  const [lastError, setLastError] = useState<string|undefined>(undefined);
  const [downloading, setDownloading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  const context = useContext(SearchComponentContext);

  //when the selected object changes, reset the download status
  useEffect(()=>{
    setLastError(undefined);
    setDownloading(false);
    setSucceeded(false);
  }, [props.oid]);

  const performDownload = async () => {
    setDownloading(true);
    setLastError(undefined);

    const result = await authenticatedFetch(
      `/api/vault/${context.vaultId}/${props.oid}/token`,
        {}
    );
    setDownloading(false);

    switch (result.status) {
      case 200:
        const serverData = await result.json();
        const a = document.createElement("a");
        console.log("token download uri is ", serverData.uri);
        a.href = serverData.uri;
        a.download = props.fileName;
        a.target = "_blank";
        a.click();
        setSucceeded(true);
        setLastError(undefined);
        break;
      case 404:
        setSucceeded(false);
        setLastError("This item does not exist on the server or download token expired");
        break;
      case 502:
      case 503:
      case 504:
        setSucceeded(false);
        setLastError("Server is not responding, will retry in 3s...")
        window.setTimeout(() => performDownload(), 3000);

        break;
      default:
        setDownloading(false);
        setSucceeded(false);
        setLastError("Server error, see logs")
    }
  }

    return (
      <div>
        {downloading ? (
          <LoadingIndicator messageText="Downloading..." />
        ) : (
          <button onClick={performDownload}>
            &gt;&nbsp;&nbsp;Download&nbsp;&nbsp;&lt;
          </button>
        )}
        {succeeded ? (
          <Typography>Download running, please check your browser downloads</Typography>
        ) : null}
        {lastError ? (
          <Typography className="error">{lastError}</Typography>
        ) : null}
      </div>
    );
}

export default DownloadButton;
