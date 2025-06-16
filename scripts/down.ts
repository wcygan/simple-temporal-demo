#!/usr/bin/env deno run --allow-all
/**
 * Stop the Content Approval System infrastructure and application
 */

import { $ } from "@david/dax";
import { bold, cyan, green, red, yellow } from "@std/fmt/colors";

const COMPOSE_FILE = "content-approval/docker-compose.yml";

async function main() {
  console.log(bold(cyan("🛑 Stopping Content Approval System")));
  
  try {
    // Stop any running Quarkus processes
    await stopApplication();
    
    // Stop Docker Compose services
    await stopInfrastructure();
    
    // Optional: Clean up resources
    await cleanup();
    
    console.log(bold(green("✅ Content Approval System stopped successfully")));
    
  } catch (error) {
    console.error(red(`❌ Failed to stop system: ${(error as Error).message}`));
    Deno.exit(1);
  }
}

async function stopApplication(): Promise<void> {
  console.log(yellow("🔄 Stopping application processes..."));
  
  try {
    // Try to find and kill any running Java/Quarkus processes
    if (Deno.build.os === "windows") {
      await $`taskkill /F /IM java.exe`.quiet();
    } else {
      // Find Java processes running Quarkus
      try {
        const processes = await $`pgrep -f "quarkus"`.text();
        if (processes.trim()) {
          await $`pkill -f "quarkus"`.quiet();
          console.log(green("✅ Stopped running application processes"));
        }
      } catch {
        // No processes found
      }
    }
  } catch {
    // No processes to stop, which is fine
    console.log(cyan("ℹ️ No application processes to stop"));
  }
}

async function stopInfrastructure(): Promise<void> {
  console.log(yellow("🐳 Stopping infrastructure services..."));
  
  try {
    // Stop and remove containers
    await $`docker-compose -f ${COMPOSE_FILE} down`.printCommand();
    console.log(green("✅ Infrastructure services stopped"));
  } catch (error) {
    console.warn(yellow(`⚠️ Failed to stop some services: ${(error as Error).message}`));
  }
}

async function cleanup(): Promise<void> {
  console.log(yellow("🧹 Cleaning up resources..."));
  
  try {
    // Clean Maven target directory
    await $`mvn clean`.cwd("content-approval").quiet();
    
    // Remove any temporary files
    try {
      await Deno.remove("content-approval/quarkus.log", { recursive: false });
    } catch {
      // File doesn't exist, which is fine
    }
    
    console.log(green("✅ Cleanup completed"));
  } catch (error) {
    console.warn(yellow(`⚠️ Cleanup warning: ${(error as Error).message}`));
  }
}

// Handle graceful shutdown
function handleShutdown() {
  console.log(yellow("\n🛑 Received shutdown signal, stopping services..."));
  main().then(() => Deno.exit(0)).catch(() => Deno.exit(1));
}

// Register signal handlers
if (Deno.build.os !== "windows") {
  Deno.addSignalListener("SIGINT", handleShutdown);
  Deno.addSignalListener("SIGTERM", handleShutdown);
}

if (import.meta.main) {
  await main();
}