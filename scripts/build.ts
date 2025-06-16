#!/usr/bin/env deno run --allow-all
/**
 * Build the Content Approval System
 */

import { $ } from "@david/dax";
import { parseArgs } from "@std/cli/parse-args";
import { bold, cyan, dim, green, red, yellow } from "@std/fmt/colors";

interface BuildOptions {
  native?: boolean;
  docker?: boolean;
  skipTests?: boolean;
  clean?: boolean;
}

async function main() {
  const args = parseArgs(Deno.args, {
    boolean: ["native", "docker", "skip-tests", "clean", "help"],
    alias: {
      h: "help",
      n: "native",
      d: "docker", 
      s: "skip-tests",
      c: "clean"
    }
  });

  if (args.help) {
    printHelp();
    return;
  }

  const options: BuildOptions = {
    native: args.native,
    docker: args.docker,
    skipTests: args["skip-tests"],
    clean: args.clean
  };

  console.log(bold(cyan("🔨 Building Content Approval System")));
  
  try {
    // Clean if requested
    if (options.clean) {
      await cleanProject();
    }
    
    // Generate jOOQ classes
    await generateCode();
    
    // Run the appropriate build
    if (options.native) {
      await buildNative(options);
    } else if (options.docker) {
      await buildDocker(options);
    } else {
      await buildJVM(options);
    }
    
    console.log(bold(green("✅ Build completed successfully!")));
    
  } catch (error) {
    console.error(red(`❌ Build failed: ${(error as Error).message}`));
    Deno.exit(1);
  }
}

async function cleanProject(): Promise<void> {
  console.log(yellow("🧹 Cleaning project..."));
  
  await $`mvn clean`.cwd("content-approval").printCommand();
  
  // Remove any additional build artifacts
  try {
    await Deno.remove("content-approval/quarkus.log");
  } catch {
    // File doesn't exist, which is fine
  }
  
  console.log(green("✅ Project cleaned"));
}

async function generateCode(): Promise<void> {
  console.log(yellow("🔨 Generating jOOQ classes..."));
  
  // Ensure Docker is running for TestContainers code generation
  try {
    await $`docker info`.quiet();
  } catch {
    throw new Error("Docker is required for jOOQ code generation with TestContainers");
  }
  
  await $`mvn clean compile`.cwd("content-approval").printCommand();
  console.log(green("✅ Code generation completed"));
}

async function buildJVM(options: BuildOptions): Promise<void> {
  console.log(bold(yellow("☕ Building JVM application")));
  
  let cmd = ["mvn", "package"];
  
  if (options.skipTests) {
    cmd.push("-DskipTests");
  }
  
  await $`${cmd}`.cwd("content-approval").printCommand();
  
  console.log(green("✅ JVM build completed"));
  console.log(cyan("📦 Artifacts:"));
  console.log(`   • JAR: ${dim("content-approval/target/quarkus-app/quarkus-run.jar")}`);
  console.log(`   • Lib: ${dim("content-approval/target/quarkus-app/lib/")}`);
  console.log("");
  console.log(cyan("🚀 Run with: java -jar content-approval/target/quarkus-app/quarkus-run.jar"));
}

async function buildNative(options: BuildOptions): Promise<void> {
  console.log(bold(yellow("⚡ Building native executable")));
  console.log(dim("Note: This may take several minutes..."));
  
  let cmd = ["mvn", "package", "-Pnative"];
  
  if (options.skipTests) {
    cmd.push("-DskipTests");
  }
  
  // Use container build by default for better compatibility
  cmd.push("-Dquarkus.native.container-build=true");
  
  await $`${cmd}`.cwd("content-approval").printCommand();
  
  console.log(green("✅ Native build completed"));
  console.log(cyan("📦 Artifacts:"));
  console.log(`   • Native executable: ${dim("content-approval/target/content-approval-1.0.0-SNAPSHOT-runner")}`);
  console.log("");
  console.log(cyan("🚀 Run with: ./content-approval/target/content-approval-1.0.0-SNAPSHOT-runner"));
}

async function buildDocker(options: BuildOptions): Promise<void> {
  console.log(bold(yellow("🐳 Building Docker image")));
  
  let cmd = ["mvn", "package"];
  
  if (options.skipTests) {
    cmd.push("-DskipTests");
  }
  
  // Build container image
  cmd.push("-Dquarkus.container-image.build=true");
  
  if (options.native) {
    cmd.push("-Pnative");
    cmd.push("-Dquarkus.native.container-build=true");
  }
  
  await $`${cmd}`.cwd("content-approval").printCommand();
  
  console.log(green("✅ Docker build completed"));
  
  // List built images
  try {
    const images = await $`docker images | grep content-approval`.text();
    if (images.trim()) {
      console.log(cyan("📦 Docker images:"));
      console.log(dim(images));
    }
  } catch {
    // No images found or docker not available
  }
}

function printHelp(): void {
  console.log(bold("Content Approval System Build Tool"));
  console.log("");
  console.log("USAGE:");
  console.log("  deno task build [OPTIONS]");
  console.log("");
  console.log("OPTIONS:");
  console.log("  -n, --native         Build native executable (GraalVM)");
  console.log("  -d, --docker         Build Docker container image");
  console.log("  -s, --skip-tests     Skip running tests during build");
  console.log("  -c, --clean          Clean before building");
  console.log("  -h, --help           Show this help");
  console.log("");
  console.log("EXAMPLES:");
  console.log("  deno task build                    # Standard JVM build");
  console.log("  deno task build --native           # Native executable build");
  console.log("  deno task build --docker           # Docker image build");
  console.log("  deno task build --clean --native   # Clean native build");
  console.log("  deno task build --skip-tests       # Fast build without tests");
  console.log("");
  console.log("BUILD TYPES:");
  console.log("  JVM:     Fast startup, standard Java deployment");
  console.log("  Native:  Instant startup, minimal memory, longer build time");
  console.log("  Docker:  Containerized deployment, includes base image");
}

if (import.meta.main) {
  await main();
}