import React, { useEffect, useState } from "react";
import { authenticatedFetch } from "../auth";
import { Input, Tooltip } from "@material-ui/core";
import { Search } from "@material-ui/icons";

const defaultContentConverter: ValueConverterFunc<NameValuePair[]> = (
  incomingData: NameValuePair[]
) => incomingData;

type SearchDocCallback = (currentSearch: string) => any;

interface FilterableListProps<T> {
  unfilteredContent?: NameValuePair[];
  unfilteredContentFetchUrl?: string;
  makeSearchDoc?: SearchDocCallback; //pass in the currently selected value and get back a JSON document to PUT to the server
  fetchUrlFilterQuery?: string;
  unfilteredContentConverter?: ValueConverterFunc<T>;
  onChange: (newValue: string) => void;
  value?: string;
  onFiltered?: (filterValue: string) => void;
  size: number;
  allowCredentials?: boolean;
  triggerRefresh?: number;
}

const FilterableList: React.FC<FilterableListProps<any>> = (props) => {
  const [currentSearch, setCurrentSearch] = useState("");
  const [contentFromServer, setContentFromServer] = useState<NameValuePair[]>(
    []
  );
  const [filteredStaticContent, setFilteredStaticContent] = useState<
    NameValuePair[]
  >([]);

  async function fetchFromServer(searchParam: string) {
    const getUrl =
      props.unfilteredContentFetchUrl +
      "?" +
      props.fetchUrlFilterQuery +
      "=" +
      searchParam;
    const credentialsValue = props.allowCredentials ? "include" : "omit";

    let searchDoc = null;
    try {
      if (props.makeSearchDoc) {
        searchDoc = props.makeSearchDoc(searchParam);
        if (searchDoc == null) {
          setContentFromServer([]);
          return;
        }
      }
    } catch (e) {
      console.log("Could not build search doc: ", e);
      setContentFromServer([]);
      return;
    }

    const result = await (searchDoc
      ? authenticatedFetch(props.unfilteredContentFetchUrl, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(searchDoc),
          credentials: credentialsValue,
        })
      : authenticatedFetch(getUrl, {
          method: "GET",
          credentials: credentialsValue,
        }));

    const content = await result.json();

    try {
      if (!result.ok) {
        console.error(`FilterableList got a server ${result.status} error`);
        setContentFromServer([]);
        return;
      }

      const convertedContent = props.unfilteredContentConverter
        ? props.unfilteredContentConverter(content)
        : defaultContentConverter(content);
      setContentFromServer(convertedContent);
    } catch (err) {
      console.error("Could not convert content: ", err);
    }
  }

  /**
   * perform a filter operation on the provided static content.  This function does not _need_ to be async because
   * of the function itself, but it is declared like that to simplify the calling code so it returns Promise<void> same
   * as fetchFromServer
   * @param searchParam
   */
  async function filterStatic(searchParam: string) {
    if (props.unfilteredContent) {
      if (searchParam === "") {
        setFilteredStaticContent(props.unfilteredContent);
      } else {
        const searchParamLwr = searchParam.toLowerCase();
        setFilteredStaticContent(
          props.unfilteredContent.filter((entry) => {
            entry.name.toLowerCase().includes(searchParamLwr);
          })
        );
      }
    } else {
      console.error(
        "can't filter static when there is no static content to be filtered"
      );
    }
  }

  useEffect(() => {
    const completionPromise = props.unfilteredContentFetchUrl
      ? fetchFromServer(currentSearch)
      : filterStatic(currentSearch);

    completionPromise
      .then(() => {
        if (props.onFiltered) props.onFiltered(currentSearch);
      })
      .catch((err) => {
        console.error("could not load in content: ", err);
      });
  }, [currentSearch, props.triggerRefresh, props.unfilteredContent]);

  const listContent = props.unfilteredContent
    ? filteredStaticContent
    : contentFromServer;
  const sortedContent = listContent.sort((a, b) =>
    a.name.localeCompare(b.name)
  );

  return (
    <div className="filterable-list-holder">
      <ul className="no-decorations">
        <li className="filterable-list-entry">
          <Search className="inline-icon" />
          <Tooltip title="Search here">
            <Input
              onChange={(evt) => setCurrentSearch(evt.target.value)}
              value={currentSearch}
              type="text"
              style={{ width: "90%" }}
            />
          </Tooltip>
        </li>
        <li className="filterable-list-entry">
          <select
            className="filterable-list-selector"
            size={props.size}
            onChange={(evt) => props.onChange(evt.target.value)}
            value={props.value}
          >
            {sortedContent.map((elem, idx) => (
              <option key={idx} value={elem.value}>
                {elem.name}
              </option>
            ))}
          </select>
        </li>
      </ul>
    </div>
  );
};

export default FilterableList;
