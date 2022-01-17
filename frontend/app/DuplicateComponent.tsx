import React from "react";
import {RouteComponentProps, withRouter} from "react-router-dom";
import { authenticatedFetch } from "./auth";
import VaultSelector from "./searchnbrowse/NewVaultSelector";

interface DuplicateComponentState {
  vaultId?: string;
  duplicatesCount?: string;
  itemCount?: string;
  duplicates?: DuplicateEntry[];
}

interface DuplicateEntry {
  mxfsPath: string;
  duplicateNumber: number;
  duplicatesData: DuplicateData[];
}

interface DuplicateData {
  mxfsPath: string;
  oid: string;
  mxfsFilename: string;
  maybeAssetFolder?: string;
  maybeProject?: string;
  maybeType?: string;
}

class DuplicateComponent extends React.Component<RouteComponentProps, DuplicateComponentState> {

  constructor(props:RouteComponentProps) {
    super(props);

    this.state = {
      vaultId: undefined,
      duplicatesCount: undefined,
      itemCount: undefined,
      duplicates: undefined
    };
  }

  async getDuplicateData() {
    const abortController = new AbortController();

    const response = await authenticatedFetch('/api/vault/'+ this.state.vaultId +'/findDuplicates', {
      signal: abortController.signal,
    });
    if (response.status !== 200) {
      console.error(`Could not load data: server error ${response.status}`);
      const rawData = await response.text();
      console.error(`Server said ${rawData}`);

      return;
    } else {
      const content = await response.json();
      this.setState({ duplicatesCount: content.dupes_count });
      this.setState({ itemCount: content.item_count });
      this.setState({duplicates: content.duplicates})
    }

  }

  componentDidUpdate(prevProps: Readonly<RouteComponentProps>, prevState: Readonly<DuplicateComponentState>, snapshot?: any) {
    if (this.state.vaultId !== prevState.vaultId) {
      this.getDuplicateData();
    }
  }

  render() {
    return (
      <div className="windowpanel">
        <VaultSelector
            currentvault={this.state.vaultId ?? ""}
            vaultWasChanged={(newVault) => {
              this.setState({ duplicatesCount: undefined });
              this.setState({ itemCount: undefined });
              this.setState({ vaultId: newVault });
            }}
        />
        <br />
        <br />
        {this.state.duplicatesCount ? (
            <div>Duplicates in vault: {this.state.duplicatesCount}</div>
        ) :(<div>Loading duplicate data...</div>)
        }

        {this.state.itemCount ? (
            <div>Items in vault: {this.state.itemCount}</div>
        ) :(null)
        }
        <br />
        <br />
        {this.state.duplicates ? (
            this.state.duplicates.map((item,i) => (<div key={i}>Path: {item.mxfsPath}<br/> Duplicates: {item.duplicateNumber}{
              item.duplicatesData ? (
              item.duplicatesData.map((itemsub, i) => (
                  <div key={i}>
                    <table>
                      <tr><th>ObjectMatrix Id.</th><td>{itemsub.oid}</td></tr>
                      <tr><th>Path</th><td>{itemsub.mxfsPath}</td></tr>
                      <tr><th>Filename</th><td>{itemsub.mxfsFilename}</td></tr>
                    {itemsub.maybeAssetFolder ? (
                        <tr><th>Asset Folder</th><td>{itemsub.maybeAssetFolder}</td></tr>
                      ) : (null)
                    }
                    {itemsub.maybeType ? (
                        <tr><th>Type</th><td>{itemsub.maybeType}</td></tr>
                      ) : (null)
                    }
                    {itemsub.maybeProject ? (
                        <tr><th>Project Id.</th><td>{itemsub.maybeProject}</td></tr>
                      ) : (null)
                    }
                    </table>
                  </div>
                  )
              ) ): (null)
            }
            <br/><br/></div>))
        ) : (null)
        }
      </div>
    );
  }
}

export default withRouter(DuplicateComponent);