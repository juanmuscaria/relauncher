package com.juanmuscaria.relauncher.smoketest;

import com.juanmuscaria.relauncher.CommandLineProvider;

import java.util.Collections;
import java.util.List;

public class HelloWorldProvider implements CommandLineProvider {

    @Override
    public List<String> extraJvmArguments() {
        return Collections.singletonList("-Drelauncher.smoketest=hello");
    }

}
