import React from "react";
import { shallow, mount } from "enzyme";
import CommissionProjectView from "../../app/metadata/CommissionProjectView.jsx";
import expect from "expect";

describe("CommissionProjectView", () => {
  it("should display an unordered list of working group->commission->project->master", () => {
    const testitem = {
      gnmMetadata: {
        type: "Rushes",
        workingGroupName: "Some WG",
        commissionName: "Some commission",
        projectName: "Some project",
        masterName: "Some master",
      },
    };
    const rendered = shallow(<CommissionProjectView entry={testitem} />);

    expect(rendered.find("li").first().text()).toEqual("Some WG");
    expect(rendered.find("li").at(1).text()).toEqual("Some commission");
    expect(rendered.find("li").at(2).text()).toEqual("Some project");
    expect(rendered.find("li").at(3).text()).toEqual("Some master");
    expect(rendered.find("li").at(3).props().style).toEqual({
      display: "inherit",
    });
    expect(rendered.find("li").length).toEqual(4);
  });

  it("should hide the master entry if it is not set", () => {
    const testitem = {
      gnmMetadata: {
        type: "Rushes",
        workingGroupName: "Some WG",
        commissionName: "Some commission",
        projectName: "Some project",
      },
    };
    const rendered = shallow(<CommissionProjectView entry={testitem} />);

    expect(rendered.find("li").first().text()).toEqual("Some WG");
    expect(rendered.find("li").at(1).text()).toEqual("Some commission");
    expect(rendered.find("li").at(2).text()).toEqual("Some project");
    expect(rendered.find("li").at(3).props().style).toEqual({
      display: "none",
    });
    expect(rendered.find("li").length).toEqual(4);
  });

  it("should display placeholder text if there is no working group set", () => {
    const testitem = {
      gnmMetadata: {
        type: "Rushes",
        commissionName: "Some commission",
        projectName: "Some project",
        masterName: "Some master",
      },
    };
    const rendered = shallow(<CommissionProjectView entry={testitem} />);

    expect(rendered.find("li").first().text()).toEqual("(no working group)");
    expect(rendered.find("li").at(1).text()).toEqual("Some commission");
    expect(rendered.find("li").at(2).text()).toEqual("Some project");
    expect(rendered.find("li").at(3).text()).toEqual("Some master");
    expect(rendered.find("li").at(3).props().style).toEqual({
      display: "inherit",
    });
    expect(rendered.find("li").length).toEqual(4);
  });

  it("should display an empty div if item is not set", () => {
    const rendered = shallow(<CommissionProjectView entry={null} />);
    expect(rendered.find("li").length).toEqual(0);
    expect(rendered.find("div").length).toEqual(1);
    expect(rendered.find("div").text()).toEqual("");
  });

  it("should display an empty div if the item has no gnmmetadata field", () => {
    const testitem = {
      key: "value",
    };
    const rendered = shallow(<CommissionProjectView entry={testitem} />);
    expect(rendered.find("li").length).toEqual(0);
    expect(rendered.find("div").length).toEqual(1);
    expect(rendered.find("div").text()).toEqual("");
  });

  it("should hide the div if gnm tpye is not set", () => {
    const testitem = {
      gnmMetadata: {
        commissionName: "Some commission",
        projectName: "Some project",
        masterName: "Some master",
      },
    };
    const rendered = shallow(<CommissionProjectView entry={testitem} />);
    expect(rendered.find("div.comm-project-locator").length).toEqual(1);
    expect(rendered.find("div.comm-project-locator").props().style).toEqual({
      display: "none",
    });
  });
});
