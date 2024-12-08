import os

# Base structure
services = [
    ("payment-service", "Payment"),
    ("transaction-service", "Transaction"),
    ("reconciliation-service", "Reconciliation"),
]

base_path = "microservices"

# Template for Java files
application_template = """package com.example.{service_lower};

public class {service}ServiceApplication {{
    public static void main(String[] args) {{
        System.out.println("{service} Service is running!");
    }}
}}
"""

controller_template = """package com.example.{service_lower};

public class {service}Controller {{
    // Add controller logic here
}}
"""

service_template = """package com.example.{service_lower};

public class {service}Service {{
    // Add service logic here
}}
"""

# Template for `Dockerfile`
dockerfile_content = """FROM openjdk:17-jdk-slim
COPY target/{service_lower}-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
"""

# Template for `pom.xml`
pom_xml_content = """<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>{service_lower}</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <dependencies>
        <!-- Add dependencies here -->
    </dependencies>
</project>
"""

# Create directories and files
for service_name, service in services:
    service_path = os.path.join(base_path, service_name, "src", "main", "java", "com", "example", service_name.replace("-", ""))
    os.makedirs(service_path, exist_ok=True)

    # Java files
    with open(os.path.join(service_path, f"{service}ServiceApplication.java"), "w") as f:
        f.write(application_template.format(service=service, service_lower=service_name.replace("-", "")))
    with open(os.path.join(service_path, f"{service}Controller.java"), "w") as f:
        f.write(controller_template.format(service=service, service_lower=service_name.replace("-", "")))
    with open(os.path.join(service_path, f"{service}Service.java"), "w") as f:
        f.write(service_template.format(service=service, service_lower=service_name.replace("-", "")))

    # Dockerfile
    with open(os.path.join(base_path, service_name, "Dockerfile"), "w") as f:
        f.write(dockerfile_content.format(service_lower=service_name.replace("-", "")))

    # pom.xml
    with open(os.path.join(base_path, service_name, "pom.xml"), "w") as f:
        f.write(pom_xml_content.format(service_lower=service_name.replace("-", "")))

print(f"Structure created successfully under '{base_path}'!")
