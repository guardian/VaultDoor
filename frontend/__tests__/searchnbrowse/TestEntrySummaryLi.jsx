import React from "react";
import { shallow } from "enzyme";
import sinon from "sinon";
import EntrySummaryLi from "../../app/searchnbrowse/EntrySummaryLi";

describe("EntrySummaryLi", () => {
  it("should render a list entry with the given object details", () => {
    const mockCb = sinon.spy();
    const fakeEntry = {
      oid: "1234567",
      attributes: {
        name: "path/to/some/file.mxf",
        size: 1234,
        ctime: "2020-01-02T03:04:05Z",
      },
      metadata: "KEY=value, ANOTHERKEY=anothervalue",
    };

    const rendered = shallow(
      <EntrySummaryLi entry={fakeEntry} entryClickedCb={mockCb} />
    );

    expect(rendered.find("li.clickable")).toBeTruthy();
    expect(rendered.find("p.filename").text()).toEqual("file.mxf");
    const sups = rendered.find("p.supplementary");
    expect(sups.length).toEqual(2);
    expect(sups.at(0).text()).toEqual("1234 ");
    expect(sups.at(1).text()).toEqual("2020-01-02T03:04:05Z");
    expect(rendered.find("FontAwesomeIcon").length).toEqual(0);
  });

  it("should show a rubbish bin icon if MXFS_INTRASH=true", () => {
    const mockCb = sinon.spy();
    const fakeEntry = {
      oid: "1234567",
      attributes: {
        name: "path/to/some/file.mxf",
        size: 1234,
        ctime: "2020-01-02T03:04:05Z",
      },
      metadata: "KEY=value, ANOTHERKEY=anothervalue, MXFS_INTRASH=true",
    };

    const rendered = shallow(
      <EntrySummaryLi entry={fakeEntry} entryClickedCb={mockCb} />
    );

    expect(rendered.find("li.clickable")).toBeTruthy();
    expect(rendered.find("p.filename").text()).toEqual("file.mxf");
    const sups = rendered.find("p.supplementary");
    expect(sups.length).toEqual(2);
    expect(sups.at(1).text()).toEqual("2020-01-02T03:04:05Z");
    const icon = rendered.find("#delete-icon");
    expect(icon.length).toEqual(1);
  });
});
