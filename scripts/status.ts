#!/usr/bin/env deno run --allow-all
/**
 * Check the status of the Content Approval System
 */

import { $ } from "@david/dax";
import { bold, cyan, dim, green, red, yellow } from "@std/fmt/colors";

interface ServiceStatus {
  name: string;
  status: "running" | "stopped" | "unhealthy" | "unknown";
  port?: number;
  url?: string;
  health?: string;
}

async function main() {
  console.log(bold(cyan("📊 Content Approval System Status")));
  console.log("");
  
  try {
    const services = await checkAllServices();
    displayServiceStatus(services);
    
    await checkApplicationEndpoints();
    
  } catch (error) {
    console.error(red(`❌ Status check failed: ${(error as Error).message}`));
    Deno.exit(1);
  }
}

async function checkAllServices(): Promise<ServiceStatus[]> {
  const services: ServiceStatus[] = [];
  
  // Check Docker services
  try {
    const dockerServices = await checkDockerServices();
    services.push(...dockerServices);
  } catch (error) {
    console.warn(yellow(`⚠️ Docker check failed: ${(error as Error).message}`));
  }
  
  // Check application
  const appStatus = await checkApplicationStatus();
  services.push(appStatus);
  
  return services;
}

async function checkDockerServices(): Promise<ServiceStatus[]> {
  const services: ServiceStatus[] = [];
  
  try {
    const result = await $`docker-compose -f content-approval/docker-compose.yml ps --format json`.text();
    const dockerServices = JSON.parse(`[${result.trim().split('\n').join(',')}]`);
    
    for (const service of dockerServices) {
      const status: ServiceStatus = {
        name: service.Service,
        status: service.State === "running" ? "running" : "stopped",
        health: service.Health || undefined
      };
      
      // Add port information
      if (service.Service === "mysql") {
        status.port = 3306;
      } else if (service.Service === "temporal") {
        status.port = 7233;
      } else if (service.Service === "temporal-ui") {
        status.port = 8081;
        status.url = "http://localhost:8081";
      }
      
      services.push(status);
    }
  } catch {
    // No services running
    services.push(
      { name: "mysql", status: "stopped", port: 3306 },
      { name: "temporal", status: "stopped", port: 7233 },
      { name: "temporal-ui", status: "stopped", port: 8081 }
    );
  }
  
  return services;
}

async function checkApplicationStatus(): Promise<ServiceStatus> {
  try {
    // Try to connect to the health endpoint
    const response = await fetch("http://localhost:8088/q/health", {
      signal: AbortSignal.timeout(5000)
    });
    
    if (response.ok) {
      const health = await response.json();
      return {
        name: "content-approval",
        status: health.status === "UP" ? "running" : "unhealthy",
        port: 8088,
        url: "http://localhost:8088",
        health: health.status
      };
    } else {
      return {
        name: "content-approval",
        status: "unhealthy",
        port: 8088
      };
    }
  } catch {
    return {
      name: "content-approval",
      status: "stopped",
      port: 8088
    };
  }
}

function displayServiceStatus(services: ServiceStatus[]): void {
  console.log(bold("🔧 Infrastructure Services"));
  console.log("─".repeat(50));
  
  for (const service of services) {
    const statusIcon = getStatusIcon(service.status);
    const statusColor = getStatusColor(service.status);
    
    let line = `${statusIcon} ${bold(service.name.padEnd(15))} ${statusColor(service.status.toUpperCase())}`;
    
    if (service.port) {
      line += dim(` :${service.port}`);
    }
    
    if (service.health && service.health !== service.status) {
      line += dim(` (${service.health})`);
    }
    
    console.log(line);
  }
  
  console.log("");
}

async function checkApplicationEndpoints(): Promise<void> {
  const endpoints = [
    { name: "Health Check", url: "http://localhost:8088/q/health" },
    { name: "Dev UI", url: "http://localhost:8088/q/dev/" },
    { name: "Swagger UI", url: "http://localhost:8088/q/swagger-ui/" },
    { name: "Metrics", url: "http://localhost:8088/q/metrics" },
    { name: "Temporal UI", url: "http://localhost:8081" }
  ];
  
  console.log(bold("🌐 Application Endpoints"));
  console.log("─".repeat(50));
  
  for (const endpoint of endpoints) {
    try {
      const response = await fetch(endpoint.url, {
        signal: AbortSignal.timeout(3000),
        method: "HEAD"
      });
      
      const status = response.ok ? "accessible" : "error";
      const icon = response.ok ? "🟢" : "🔴";
      const color = response.ok ? green : red;
      
      console.log(`${icon} ${endpoint.name.padEnd(15)} ${color(status.toUpperCase())} ${dim(endpoint.url)}`);
    } catch {
      console.log(`🔴 ${endpoint.name.padEnd(15)} ${red("UNREACHABLE")} ${dim(endpoint.url)}`);
    }
  }
  
  console.log("");
}

function getStatusIcon(status: string): string {
  switch (status) {
    case "running": return "🟢";
    case "stopped": return "🔴";
    case "unhealthy": return "🟡";
    default: return "⚪";
  }
}

function getStatusColor(status: string) {
  switch (status) {
    case "running": return green;
    case "stopped": return red;
    case "unhealthy": return yellow;
    default: return dim;
  }
}

if (import.meta.main) {
  await main();
}