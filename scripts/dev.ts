#!/usr/bin/env deno run --allow-all
/**
 * Start the Content Approval System in development mode
 */

import { $ } from "@david/dax";
import { bold, cyan, dim, green, red, yellow } from "@std/fmt/colors";

async function main() {
  console.log(bold(cyan("🚀 Starting Content Approval System in Development Mode")));
  
  try {
    // Check prerequisites
    await checkPrerequisites();
    
    // Ensure infrastructure is running
    await ensureInfrastructure();
    
    // Generate jOOQ classes
    await generateCode();
    
    // Start Quarkus in dev mode
    await startDevMode();
    
  } catch (error) {
    console.error(red(`❌ Failed to start development mode: ${(error as Error).message}`));
    Deno.exit(1);
  }
}

async function checkPrerequisites(): Promise<void> {
  console.log(yellow("🔍 Checking prerequisites..."));
  
  // Check Java
  try {
    const javaVersion = await $`java -version`.text().catch(() => 
      $`java --version`.text()
    );
    console.log(green("✅ Java is available"));
    if (javaVersion.includes("21")) {
      console.log(dim("   Using Java 21"));
    }
  } catch {
    throw new Error("Java 21+ is required but not found in PATH");
  }
  
  // Check Maven
  try {
    await $`mvn --version`.quiet();
    console.log(green("✅ Maven is available"));
  } catch {
    throw new Error("Maven is required but not found in PATH");
  }
  
  // Check Docker
  try {
    await $`docker info`.quiet();
    console.log(green("✅ Docker is running"));
  } catch {
    throw new Error("Docker is not running. Please start Docker Desktop for TestContainers support.");
  }
}

async function ensureInfrastructure(): Promise<void> {
  console.log(yellow("🐳 Checking infrastructure services..."));
  
  try {
    // Check if services are already running
    const result = await $`docker-compose -f content-approval/docker-compose.yml ps --format json`.text();
    
    if (result.trim()) {
      const services = JSON.parse(`[${result.trim().split('\n').join(',')}]`);
      const runningServices = services.filter((s: any) => s.State === "running");
      
      if (runningServices.length >= 2) { // mysql and temporal
        console.log(green("✅ Infrastructure services are already running"));
        return;
      }
    }
    
    console.log(yellow("🚀 Starting infrastructure services..."));
    await $`docker-compose -f content-approval/docker-compose.yml up -d`.printCommand();
    
    // Wait a moment for services to start
    console.log(yellow("⏳ Waiting for services to initialize..."));
    await new Promise(resolve => setTimeout(resolve, 10000));
    
    console.log(green("✅ Infrastructure services started"));
    
  } catch (error) {
    throw new Error(`Failed to start infrastructure: ${(error as Error).message}`);
  }
}

async function generateCode(): Promise<void> {
  console.log(yellow("🔨 Generating jOOQ classes..."));
  
  try {
    await $`mvn clean compile`.cwd("content-approval").printCommand();
    console.log(green("✅ Code generation completed"));
  } catch (error) {
    throw new Error(`Code generation failed: ${(error as Error).message}`);
  }
}

async function startDevMode(): Promise<void> {
  console.log(bold(yellow("🔥 Starting Quarkus Development Mode")));
  console.log(dim("Press 'q' to quit, 'h' for help, 'r' to restart"));
  
  console.log(cyan("📋 Development endpoints will be available at:"));
  console.log("   • Application: http://localhost:8088");
  console.log("   • Dev UI: http://localhost:8088/q/dev/");
  console.log("   • Health: http://localhost:8088/q/health");
  console.log("   • Swagger: http://localhost:8088/q/swagger-ui/");
  console.log("   • Temporal UI: http://localhost:8081");
  console.log("");
  
  try {
    // Start Quarkus dev mode with proper signal handling
    const process = $`mvn quarkus:dev`
      .cwd("content-approval")
      .env("MAVEN_OPTS", "-Xmx2048m") // Ensure enough memory
      .spawn();
    
    // Handle Ctrl+C gracefully
    const handleShutdown = () => {
      console.log(yellow("\n🛑 Shutting down development server..."));
      process.kill("SIGTERM");
    };
    
    if (Deno.build.os !== "windows") {
      Deno.addSignalListener("SIGINT", handleShutdown);
      Deno.addSignalListener("SIGTERM", handleShutdown);
    }
    
    await process;
    
  } catch (error: any) {
    if (error.code === 130) { // SIGINT
      console.log(green("\n✅ Development server stopped"));
    } else {
      throw new Error(`Development server failed: ${(error as Error).message}`);
    }
  }
}

if (import.meta.main) {
  await main();
}