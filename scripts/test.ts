#!/usr/bin/env deno run --allow-all
/**
 * Run tests for the Content Approval System
 */

import { $ } from "@david/dax";
import { parseArgs } from "@std/cli/parse-args";
import { bold, cyan, dim, green, red, yellow } from "@std/fmt/colors";

interface TestOptions {
  unit?: boolean;
  integration?: boolean;
  coverage?: boolean;
  verbose?: boolean;
}

async function main() {
  const args = parseArgs(Deno.args, {
    boolean: ["unit", "integration", "coverage", "verbose", "help"],
    alias: {
      h: "help",
      u: "unit", 
      i: "integration",
      c: "coverage",
      v: "verbose"
    }
  });

  if (args.help) {
    printHelp();
    return;
  }

  const options: TestOptions = {
    unit: args.unit,
    integration: args.integration, 
    coverage: args.coverage,
    verbose: args.verbose
  };

  // Default to all tests if no specific type specified
  if (!options.unit && !options.integration) {
    options.unit = true;
    options.integration = true;
  }

  console.log(bold(cyan("🧪 Running Content Approval System Tests")));
  
  try {
    // Ensure jOOQ classes are generated
    await ensureCodeGeneration();
    
    // Run requested test suites
    if (options.unit) {
      await runUnitTests(options);
    }
    
    if (options.integration) {
      await runIntegrationTests(options);
    }
    
    console.log(bold(green("✅ All tests completed successfully!")));
    
  } catch (error) {
    console.error(red(`❌ Tests failed: ${(error as Error).message}`));
    Deno.exit(1);
  }
}

async function ensureCodeGeneration(): Promise<void> {
  console.log(yellow("🔨 Ensuring jOOQ classes are generated..."));
  
  try {
    await $`mvn clean compile`.cwd("content-approval").quiet();
    console.log(green("✅ Code generation completed"));
  } catch (error) {
    throw new Error(`Code generation failed: ${(error as Error).message}`);
  }
}

async function runUnitTests(options: TestOptions): Promise<void> {
  console.log(bold(yellow("🏃 Running Unit Tests (H2)")));
  
  try {
    let cmd = ["mvn", "test", "-Dtest=!*IntegrationTest"];
    
    if (options.coverage) {
      cmd.push("jacoco:report");
    }
    
    if (options.verbose) {
      cmd.push("-X");
    } else {
      cmd.push("-q");
    }
    
    await $`${cmd}`.cwd("content-approval").printCommand();
    console.log(green("✅ Unit tests completed"));
  } catch (error) {
    throw new Error(`Unit tests failed: ${(error as Error).message}`);
  }
}

async function runIntegrationTests(options: TestOptions): Promise<void> {
  console.log(bold(yellow("🐳 Running Integration Tests (MySQL TestContainers)")));
  console.log(dim("Note: This requires Docker to be running"));
  
  // Check Docker status
  try {
    await $`docker info`.quiet();
  } catch {
    throw new Error("Docker is not running. Integration tests require Docker for TestContainers.");
  }
  
  try {
    let cmd = ["mvn", "test", "-Dtest=*IntegrationTest"];
    
    if (options.coverage) {
      cmd.push("jacoco:report");
    }
    
    if (options.verbose) {
      cmd.push("-X");
    } else {
      cmd.push("-q");
    }
    
    await $`${cmd}`.cwd("content-approval").printCommand();
    console.log(green("✅ Integration tests completed"));
  } catch (error) {
    throw new Error(`Integration tests failed: ${(error as Error).message}`);
  }
}

function printHelp(): void {
  console.log(bold("Content Approval System Test Runner"));
  console.log("");
  console.log("USAGE:");
  console.log("  deno task test [OPTIONS]");
  console.log("");
  console.log("OPTIONS:");
  console.log("  -u, --unit           Run unit tests only (H2)");
  console.log("  -i, --integration    Run integration tests only (MySQL TestContainers)");
  console.log("  -c, --coverage       Generate coverage reports");
  console.log("  -v, --verbose        Verbose output");
  console.log("  -h, --help           Show this help");
  console.log("");
  console.log("EXAMPLES:");
  console.log("  deno task test                    # Run all tests");
  console.log("  deno task test --unit             # Unit tests only");
  console.log("  deno task test --integration      # Integration tests only");
  console.log("  deno task test --coverage         # Run with coverage");
  console.log("");
  console.log("TEST TYPES:");
  console.log("  Unit Tests:        Fast tests using H2 in-memory database");
  console.log("  Integration Tests: Full-stack tests using MySQL TestContainers");
}

if (import.meta.main) {
  await main();
}