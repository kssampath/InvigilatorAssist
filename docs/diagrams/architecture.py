"""Renders docs/architecture.png with official Azure icons.

    pip install diagrams      # also needs Graphviz (`dot`) on PATH
    python docs/diagrams/architecture.py
"""
from pathlib import Path

from diagrams import Cluster, Diagram, Edge
from diagrams.azure.compute import ContainerApps
from diagrams.azure.containers import ContainerRegistries
from diagrams.azure.databases import AzureDatabasePostgresqlServer
from diagrams.azure.identity import ManagedIdentities
from diagrams.azure.monitor import LogAnalyticsWorkspaces
from diagrams.azure.network import DNSPrivateZones
from diagrams.azure.security import KeyVaults
from diagrams.azure.storage import StorageAccounts
from diagrams.azure.web import StaticApps
from diagrams.onprem.ci import Jenkins
from diagrams.onprem.client import Users
from diagrams.onprem.iac import Terraform
from diagrams.onprem.vcs import Github

OUT = Path(__file__).resolve().parents[1] / "architecture"

graph_attr = {
    "bgcolor": "white",
    "pad": "0.6",
    "nodesep": "0.7",
    "ranksep": "1.0",
    "fontname": "Helvetica",
    "fontsize": "26",
    "labelloc": "t",
    "splines": "spline",   # curved edges keep labels next to their lines
    "compound": "true",
}
node_attr = {"fontname": "Helvetica", "fontsize": "13"}
edge_attr = {"fontname": "Helvetica", "fontsize": "11", "color": "#5b6770"}

azure_cluster = {"bgcolor": "#f3f8fc", "pencolor": "#0078d4", "penwidth": "1.6", "fontsize": "15", "fontname": "Helvetica-Bold"}
rg_cluster = {"bgcolor": "#ffffff", "pencolor": "#9aa7b0", "style": "rounded", "fontsize": "13", "fontname": "Helvetica-Bold"}
vnet_cluster = {"margin": "20", "bgcolor": "#eef6ee", "pencolor": "#3f8f4f", "style": "dashed,rounded", "fontsize": "12"}
subnet_cluster = {"bgcolor": "#ffffff", "pencolor": "#8fbf96", "style": "rounded", "fontsize": "11"}
region_cluster = {"margin": "24", "bgcolor": "#ffffff", "pencolor": "#b8c4cc", "style": "dotted,rounded", "fontsize": "12", "fontcolor": "#3b4a52"}
cicd_cluster = {"bgcolor": "#fbf6ee", "pencolor": "#c9822a", "style": "rounded", "fontsize": "13", "fontname": "Helvetica-Bold"}

with Diagram(
    "InvigilatorAssist on Azure",
    filename=str(OUT),
    outformat="png",
    show=False,
    direction="LR",
    graph_attr=graph_attr,
    node_attr=node_attr,
    edge_attr=edge_attr,
):
    users = Users("Admins, faculty\n& students")

    with Cluster("CI/CD", graph_attr=cicd_cluster):
        github = Github("GitHub repo")
        jenkins = Jenkins("Jenkins pipeline\ntest → dev → ✋ approval → prod")
        terraform = Terraform("Terraform\n(dev & prod stacks)")
        github >> Edge(label="webhook") >> jenkins
        jenkins >> Edge(label="plan / apply") >> terraform

    with Cluster("Azure subscription", graph_attr=azure_cluster):
        with Cluster("rg-invig-shared · Central India", graph_attr=rg_cluster):
            acr = ContainerRegistries("Container Registry\ninvigilator-api:<git sha>")
            tfstate = StorageAccounts("Terraform state\n(Azure AD auth, versioned)")

        with Cluster("rg-invig-dev / rg-invig-prod", graph_attr=rg_cluster):
            with Cluster("East Asia (Static Web Apps region)", graph_attr=region_cluster):
                web = StaticApps("Static Web App\nReact UI")

            with Cluster("Central India", graph_attr=region_cluster):
                kv = KeyVaults("Key Vault\nDB password · JWT secret")
                identity = ManagedIdentities("User-assigned\nmanaged identity")
                logs = LogAnalyticsWorkspaces("Log Analytics")

                with Cluster("VNet 10.40.0.0/16", graph_attr=vnet_cluster):
                    with Cluster("snet-apps 10.40.0.0/23\nContainer Apps environment", graph_attr=subnet_cluster):
                        api = ContainerApps("Container App\nSpring Boot API :8080")
                    with Cluster("snet-db 10.40.2.0/24\nprivate access only", graph_attr=subnet_cluster):
                        db = AzureDatabasePostgresqlServer("PostgreSQL 16\nFlexible Server")
                    dns = DNSPrivateZones("Private DNS zone")

    # Request path
    users >> Edge(label="HTTPS 443", color="#0078d4", penwidth="2") >> web
    web >> Edge(label="REST + JWT · HTTPS 443", color="#0078d4", penwidth="2") >> api
    api >> Edge(label="JDBC/TLS 5432", color="#3f8f4f", penwidth="2") >> db
    db - Edge(label="name resolution", style="dotted") - dns

    # Platform wiring (least-privilege roles of the app's identity)
    api - Edge(label="runs as", style="dashed") - identity
    identity >> Edge(label="Key Vault Secrets User", style="dashed") >> kv
    acr >> Edge(label="AcrPull", style="dashed") >> api
    api >> Edge(label="console & system logs", style="dotted") >> logs

    # Delivery (roles of the Jenkins service principal)
    jenkins >> Edge(label="az acr build · AcrPush", color="#c9822a") >> acr
    jenkins >> Edge(label="deploy dist/ (SWA token)", color="#c9822a") >> web
    terraform >> Edge(label="state · Blob Data Contributor", style="dotted", color="#c9822a") >> tfstate
