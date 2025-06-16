/**
 * Simple Temporal Demo - Deno Scripts Module
 * 
 * This module exports all available scripts for the Simple Temporal Demo project.
 * The main focus is the Content Approval System built with Quarkus, MySQL, and Temporal.
 * Use via deno.json tasks for streamlined development workflow.
 */

// Re-export all scripts
export * from "./up.ts";
export * from "./down.ts"; 
export * from "./test.ts";
export * from "./dev.ts";
export * from "./build.ts";
export * from "./clean.ts";
export * from "./status.ts";

// Script metadata
export const SCRIPTS = {
  up: {
    description: "Start the Content Approval System infrastructure and application",
    command: "deno task up"
  },
  down: {
    description: "Stop the Content Approval System infrastructure and application", 
    command: "deno task down"
  },
  test: {
    description: "Run tests for the Content Approval System",
    command: "deno task test"
  },
  dev: {
    description: "Start the Content Approval System in development mode",
    command: "deno task dev"
  },
  build: {
    description: "Build the Content Approval System",
    command: "deno task build"
  },
  clean: {
    description: "Clean build artifacts and logs",
    command: "deno task clean"
  },
  status: {
    description: "Check the status of all system components",
    command: "deno task status"
  }
} as const;

// Print available scripts
export function printAvailableScripts(): void {
  console.log("📋 Available Content Approval System Scripts:");
  console.log("");
  
  for (const [name, info] of Object.entries(SCRIPTS)) {
    console.log(`  ${info.command.padEnd(20)} - ${info.description}`);
  }
}