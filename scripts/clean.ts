#!/usr/bin/env deno run --allow-all
/**
 * Clean the Content Approval System build artifacts and logs
 */

import { $ } from "@david/dax";
import { bold, cyan, dim, green, red, yellow } from "@std/fmt/colors";
import { exists } from "@std/fs";

async function main() {
  console.log(bold(cyan("🧹 Cleaning Content Approval System")));
  
  try {
    await cleanMavenArtifacts();
    await cleanLogs();
    await cleanTempFiles();
    await cleanDockerResources();
    
    console.log(bold(green("✅ Cleanup completed successfully!")));
    
  } catch (error) {
    console.error(red(`❌ Cleanup failed: ${(error as Error).message}`));
    Deno.exit(1);
  }
}

async function cleanMavenArtifacts(): Promise<void> {
  console.log(yellow("🗂️ Cleaning Maven artifacts..."));
  
  try {
    await $`mvn clean`.cwd("content-approval").printCommand();
    console.log(green("✅ Maven artifacts cleaned"));
  } catch (error) {
    console.warn(yellow(`⚠️ Maven clean warning: ${(error as Error).message}`));
  }
}

async function cleanLogs(): Promise<void> {
  console.log(yellow("📄 Cleaning log files..."));
  
  const logFiles = [
    "content-approval/quarkus.log",
    "content-approval/application.log", 
    "content-approval/nohup.out",
    "content-approval/target/surefire-reports",
    "content-approval/target/failsafe-reports"
  ];
  
  let cleanedCount = 0;
  
  for (const logFile of logFiles) {
    try {
      if (await exists(logFile)) {
        await Deno.remove(logFile, { recursive: true });
        console.log(dim(`   Removed: ${logFile}`));
        cleanedCount++;
      }
    } catch (error) {
      console.warn(yellow(`⚠️ Could not remove ${logFile}: ${(error as Error).message}`));
    }
  }
  
  if (cleanedCount > 0) {
    console.log(green(`✅ Cleaned ${cleanedCount} log files`));
  } else {
    console.log(dim("   No log files to clean"));
  }
}

async function cleanTempFiles(): Promise<void> {
  console.log(yellow("🗑️ Cleaning temporary files..."));
  
  // Clean Maven temp directories
  const mavenTempDirs = [
    "content-approval/target/generated-sources",
    "content-approval/target/generated-test-sources", 
    "content-approval/target/maven-status",
    "content-approval/target/test-classes"
  ];
  
  let cleanedCount = 0;
  
  for (const dir of mavenTempDirs) {
    try {
      if (await exists(dir)) {
        await Deno.remove(dir, { recursive: true });
        console.log(dim(`   Removed: ${dir}`));
        cleanedCount++;
      }
    } catch {
      // Directory might be in use
    }
  }
  
  if (cleanedCount > 0) {
    console.log(green(`✅ Cleaned ${cleanedCount} temporary files`));
  } else {
    console.log(dim("   No temporary files to clean"));
  }
}

async function cleanDockerResources(): Promise<void> {
  console.log(yellow("🐳 Cleaning Docker resources..."));
  
  try {
    // Check if Docker is available
    await $`docker info`.quiet();
  } catch {
    console.log(dim("   Docker not available, skipping Docker cleanup"));
    return;
  }
  
  try {
    // Clean up dangling images
    await $`docker image prune -f`.quiet();
    console.log(green("✅ Cleaned dangling Docker images"));
    
  } catch (error) {
    console.warn(yellow(`⚠️ Docker cleanup warning: ${(error as Error).message}`));
  }
}

if (import.meta.main) {
  await main();
}