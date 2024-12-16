import os

def create_file(filepath, content=""):
    """Creates a file with the given content."""
    with open(filepath, 'w') as file:
        file.write(content)

def create_payment_service_structure():
    base_dir = "payment-service-1"

    # Directory structure
    directories = [
        f"{base_dir}/src/main/java/com/psredemobank/payments/controller",
        f"{base_dir}/src/main/java/com/psredemobank/payments/model",
        f"{base_dir}/src/main/java/com/psredemobank/payments/service",
        f"{base_dir}/src/main/resources",
    ]

    # Create directories
    for directory in directories:
        os.makedirs(directory, exist_ok=True)

    # Files and their contents
    files = {
        f"{base_dir}/Dockerfile": "# Dockerfile for payment-service-1",
        f"{base_dir}/pom.xml": "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" ...></project>",
        f"{base_dir}/src/main/java/com/psredemobank/payments/controller/PaymentController.java": "package com.psredemobank.payments.controller;\n\npublic class PaymentController {\n\n}",
        f"{base_dir}/src/main/java/com/psredemobank/payments/model/PaymentRequest.java": "package com.psredemobank.payments.model;\n\npublic class PaymentRequest {\n\n}",
        f"{base_dir}/src/main/java/com/psredemobank/payments/service/PaymentService.java": "package com.psredemobank.payments.service;\n\npublic class PaymentService {\n\n}",
        f"{base_dir}/src/main/java/com/psredemobank/payments/PaymentServiceApplication.java": (
            "package com.psredemobank.payments;\n\n"
            "import org.springframework.boot.SpringApplication;\n"
            "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
            "@SpringBootApplication\n"
            "public class PaymentServiceApplication {\n\n"
            "    public static void main(String[] args) {\n"
            "        SpringApplication.run(PaymentServiceApplication.class, args);\n"
            "    }\n"
            "}"
        ),
        f"{base_dir}/src/main/resources/application.properties": "# Application properties",
        f"{base_dir}/src/main/resources/logback-spring.xml": "<configuration></configuration>",
    }

    # Create files
    for filepath, content in files.items():
        create_file(filepath, content)

if __name__ == "__main__":
    create_payment_service_structure()
    print("Payment service-1 structure created successfully.")