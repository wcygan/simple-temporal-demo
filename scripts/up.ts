#!/usr/bin/env deno run --allow-all
/**
 * Start the Content Approval System infrastructure and application
 */

import { $ } from "@david/dax";
import { bold, cyan, green, red, yellow } from "@std/fmt/colors";

const COMPOSE_FILE = "content-approval/docker-compose.yml";
const REQUIRED_SERVICES = ["mysql", "temporal", "temporal-ui"];

async function main() {
  console.log(bold(cyan("🚀 Starting Content Approval System")));
  
  try {
    // Check if Docker is running
    await checkDockerStatus();
    
    // Start infrastructure services
    await startInfrastructure();
    
    // Wait for services to be healthy
    await waitForServices();
    
    // Start the application
    await startApplication();
    
    console.log(bold(green("✅ Content Approval System is ready!")));
    console.log(cyan("📋 Available endpoints:"));
    console.log("   • Application: http://localhost:8088");
    console.log("   • Dev UI: http://localhost:8088/q/dev/");
    console.log("   • Health: http://localhost:8088/q/health");
    console.log("   • Swagger: http://localhost:8088/q/swagger-ui/");
    console.log("   • Temporal UI: http://localhost:8081");
    console.log("   • MySQL: localhost:3306");
    
  } catch (error) {
    console.error(red(`❌ Failed to start system: ${(error as Error).message}`));
    Deno.exit(1);
  }
}

async function checkDockerStatus(): Promise<void> {
  console.log(yellow("🔍 Checking Docker status..."));
  
  try {
    await $`docker info`.quiet();
    console.log(green("✅ Docker is running"));
  } catch {
    throw new Error("Docker is not running. Please start Docker Desktop.");
  }
}

async function startInfrastructure(): Promise<void> {
  console.log(yellow("🐳 Starting infrastructure services..."));
  
  // Pull latest images
  await $`docker-compose -f ${COMPOSE_FILE} pull`.printCommand();
  
  // Start services in background
  await $`docker-compose -f ${COMPOSE_FILE} up -d`.printCommand();
  
  console.log(green("✅ Infrastructure services started"));
}

async function waitForServices(): Promise<void> {
  console.log(yellow("⏳ Waiting for services to be healthy..."));
  
  const maxWaitTime = 120; // 2 minutes
  const checkInterval = 5; // 5 seconds
  let elapsed = 0;
  
  while (elapsed < maxWaitTime) {
    try {
      const result = await $`docker-compose -f ${COMPOSE_FILE} ps --format json`.text();
      const services = JSON.parse(`[${result.trim().split('\n').join(',')}]`);
      
      const healthyServices = services.filter((service: any) => 
        REQUIRED_SERVICES.includes(service.Service) && 
        (service.State === "running" || service.Health === "healthy")
      );
      
      if (healthyServices.length === REQUIRED_SERVICES.length) {
        console.log(green("✅ All services are healthy"));
        return;
      }
      
      console.log(cyan(`⏳ Waiting for services... (${elapsed}s/${maxWaitTime}s)`));
      await new Promise(resolve => setTimeout(resolve, checkInterval * 1000));
      elapsed += checkInterval;
      
    } catch (error) {
      console.warn(yellow(`⚠️ Health check failed: ${(error as Error).message}`));
      await new Promise(resolve => setTimeout(resolve, checkInterval * 1000));
      elapsed += checkInterval;
    }
  }
  
  throw new Error("Services did not become healthy within the timeout period");
}

async function startApplication(): Promise<void> {
  console.log(yellow("🔨 Building and starting application..."));
  
  // Clean and compile to ensure jOOQ classes are generated
  await $`mvn clean compile`.cwd("content-approval").printCommand();
  
  console.log(green("✅ Application compiled successfully"));
  console.log(cyan("💡 Use 'deno task dev' to start in development mode"));
  console.log(cyan("💡 Use 'mvn quarkus:dev' directly for hot reload"));
}

if (import.meta.main) {
  await main();
}