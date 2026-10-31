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
vnet_cluster = {"bgcolor": "#eef6ee", "pencolor": "#3f8f4f", "style": "dashed,rounded", "fontsize": "12"}
subnet_cluster = {"bgcolor": "#ffffff", "pencolor": "#8fbf96", "style": "rounded", "fontsize": "11"}
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
        jenkins = Jenkins("Jenkins pipeline")
        terraform = Terraform("Terraform")
        github >> Edge(label="webhook") >> jenkins
        jenkins >> Edge(label="plan / apply") >> terraform

    with Cluster("Azure subscription · Central India", graph_attr=azure_cluster):
        with Cluster("rg-invig-shared", graph_attr=rg_cluster):
            acr = ContainerRegistries("Container Registry\ninvigilator-api:<sha>")
            tfstate = StorageAccounts("Terraform state")

        with Cluster("rg-invig-dev / rg-invig-prod", graph_attr=rg_cluster):
            web = StaticApps("Static Web App\nReact UI")
            kv = KeyVaults("Key Vault\nDB password · JWT secret")
            identity = ManagedIdentities("Managed identity")
            logs = LogAnalyticsWorkspaces("Log Analytics")

            with Cluster("Virtual network", graph_attr=vnet_cluster):
                with Cluster("snet-apps (Container Apps environment)", graph_attr=subnet_cluster):
                    api = ContainerApps("Container App\nSpring Boot API")
                with Cluster("snet-db (private access only)", graph_attr=subnet_cluster):
                    db = AzureDatabasePostgresqlServer("PostgreSQL\nFlexible Server")
                dns = DNSPrivateZones("Private DNS zone")

    # Request path
    users >> Edge(label="HTTPS", color="#0078d4", penwidth="2") >> web
    web >> Edge(label="REST + JWT", color="#0078d4", penwidth="2") >> api
    api >> Edge(label="JDBC / TLS", color="#3f8f4f", penwidth="2") >> db
    db - Edge(style="dotted") - dns

    # Platform wiring
    api - Edge(style="dashed") - identity
    identity >> Edge(label="read secrets", style="dashed") >> kv
    acr >> Edge(label="AcrPull", style="dashed") >> api
    api >> Edge(label="logs", style="dotted") >> logs

    # Delivery
    jenkins >> Edge(label="az acr build", color="#c9822a") >> acr
    jenkins >> Edge(label="deploy dist/", color="#c9822a") >> web
    terraform >> Edge(label="state", style="dotted", color="#c9822a") >> tfstate
