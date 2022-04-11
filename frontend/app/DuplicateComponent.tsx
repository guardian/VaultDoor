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

      const response = await authenticatedFetch('/api/duplicatereport/' + this.state.vaultId + '/latest', {
          signal: abortController.signal,
      });
      if (response.status !== 200) {
          console.error(`Could not load data: server error ${response.status}`);
          const rawData = await response.text();
          console.error(`Server said ${rawData}`);

          return;
      } else {
          const content = await response.json();
          this.setState({ duplicatesCount: content.dupes_count, itemCount: content.item_count, duplicates: content.duplicates });
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
              this.setState({ duplicates: undefined });
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
                  <div key={i}>ObjectMatrix Id.: {itemsub.oid}</div>
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