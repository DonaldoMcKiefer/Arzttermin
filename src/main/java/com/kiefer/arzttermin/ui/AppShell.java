package com.kiefer.arzttermin.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.component.dependency.CssImport;

@CssImport("./themes/terminfinder/styles.css")
@Viewport("width=device-width, initial-scale=1")
@Meta(name = "theme-color", content = "#075f5a")
@Meta(name = "description", content = "Lokales Demo-Portal für kurzfristig freie Arzttermine")
public class AppShell implements AppShellConfigurator {
}
