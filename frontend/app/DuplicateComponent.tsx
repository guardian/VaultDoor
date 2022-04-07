import React from "react";
import {RouteComponentProps, withRouter} from "react-router-dom";
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

interface DuplicateProps {
  vault_data_path: string;
}

class DuplicateComponent extends React.Component<RouteComponentProps & DuplicateProps, DuplicateComponentState> {
  constructor(props:RouteComponentProps & DuplicateProps) {
    super(props);

    this.state = {
      vaultId: undefined,
      duplicatesCount: undefined,
      itemCount: undefined,
      duplicates: undefined
    };
  }

  async getDuplicateData() {
    try {
        fetch(this.props.vault_data_path + '/' + this.state.vaultId + '.json')
            .then(response => response.text())
            .then((data) => {
                const content = JSON.parse(data);
                this.setState({
                    duplicatesCount: content.dupes_count,
                    itemCount: content.item_count,
                    duplicates: content.duplicates
                });
            })
    } catch (e) {
      console.error(`Could not load vault data for: ` + this.state.vaultId + `. ` + e);
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