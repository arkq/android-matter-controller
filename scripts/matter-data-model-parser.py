#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 The Authors
# SPDX-License-Identifier: Apache-2.0

# Usage:
#   scripts/matter-data-model-parser.py \
#       --out-dir app/src/main/java/io/aether/android/matter
#       matter-sdk/data_model
#
# The script scans every version sub-directory inside Matter SDK data model
# dir (e.g. 1.0, 1.1, 1.2, ...) and collects ALL unique attributes, arguments,
# data types, etc. across ALL versions.
#
# Dependencies: pip install lxml

import re
import argparse
import dataclasses
import sys
from pathlib import Path

import lxml.etree as ET


def _to_pascal(s: str) -> str:
    """Convert a string to PascalCase."""
    s = re.sub(r"\W", "", s)
    return s[0].upper() + s[1:] if s else s


def _to_upper_snake(s: str) -> str:
    """Convert a string to UPPER_SNAKE_CASE."""
    # Insert an underscore before any capital letter preceded by a lowercase
    # letter or digit e.g., 'camelCase' -> 'camel_Case'
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", s)
    # Handle consecutive capitals e.g., 'HTTPResponse' -> 'HTTP_Response'
    s = re.sub(r"([A-Z])([A-Z][a-z])", r"\1_\2", s)
    # Replace all non-alphanumeric characters with underscores.
    cleaned = re.sub(r"[^A-Za-z0-9]+", "_", s).upper()
    return re.sub(r"_+", "_", cleaned).strip("_")


def _get_type_name(type: str) -> str:
    match type:
        case "fabric-idx":
            return "FabricIndex"
        case "cluster-id":
            return "ClusterId"
        case "endpoint-id" | "endpoint-no":
            return "EndpointId"
        case "group-id":
            return "GroupId"
        case "message-id":
            return "MessageId"
        case "subject-id":
            return "SubjectId"
        case "node-id":
            return "NodeId"
        case "vendor-id":
            return "VendorId"
        case "status":
            return "Status"
        case "single":
            return "Single"
        case "bool":
            return "Boolean"
        case "string":
            return "String"
        case "octstr":
            return "OctetString"
        case "int8":
            return "Int8"
        case "int16":
            return "Int16"
        case "int32":
            return "Int32"
        case "int64":
            return "Int64"
        case "uint8":
            return "UInt8"
        case "uint16":
            return "UInt16"
        case "uint24":
            return "UInt24"
        case "uint32":
            return "UInt32"
        case "uint64":
            return "UInt64"
        case "map8":
            return "Map8"
        case "map16":
            return "Map16"
        case "map32":
            return "Map32"
        case "enum8":
            return "Enum8"
        case "enum16":
            return "Enum16"
        case "hwadr":
            return "HardwareAddress"
        case "ipv6adr":
            return "IPv6Address"
        case "ipv6pre":
            return "IPv6Prefix"
        case "amperage-mA":
            return "AmperageMilliamperes"
        case "voltage-mV":
            return "VoltageMillivolts"
        case "energy-mWh":
            return "EnergyMilliwattHours"
        case "power-mW":
            return "PowerMilliwatts"
        case "elapsed-s":
            return "ElapsedSeconds"
        case "epoch-s":
            return "EpochSeconds"
        case "posix-ms":
            return "PosixMilliseconds"
        case "epoch-us":
            return "EpochMicroseconds"
        case "systime-ms":
            return "SystemTimeMilliseconds"
        case "systemtime-us":
            return "SystemTimeMicroseconds"
        case "percent":
            return "Percent"
        case "percent100ths":
            return "Percent100ths"
        case "temperature":
            return "Temperature"
        case "max 254":
            return "Max254"
    return type


def _get_type_enum(type: str) -> str:
    if type:
        return f"DataType.{_to_upper_snake(type)}"
    return "DataType.UNKNOWN"


def _get_privilege_enum(priv: str) -> str:
    if priv:
        if priv.lower() == "admin":
            priv = "ADMINISTER"
        return f"Privilege.{_to_upper_snake(priv)}"
    return "Privilege.NONE"


def _xml_files_for_version(data_model_dir: Path, version: str) -> list[Path]:
    """Return a list of all XML files for the given spec version."""
    return sorted(data_model_dir.joinpath(version).rglob("**/*.xml"))


def _xml_file_to_namespace(file: Path) -> str:
    """Convert an XML file path to a namespace prefix for enums."""
    ns = file.stem.removeprefix("bridge-clusters-").replace("-", "")
    # Ensure "Cluster" suffix to separate cluster and enum name.
    return f"{ns.removesuffix('Cluster')}Cluster"


@dataclasses.dataclass
class EnumItem:
    id: int
    name: str

    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        return bool(self.diagnostics)


@dataclasses.dataclass
class Enum:
    name: str
    entries: dict[int, EnumItem]

    def __post_init__(self):
        if self.name.upper().endswith("ENUM"):
            # Normalize the suffix to be consistent with naming conventions.
            self.name = f"{self.name[:-4]}Enum"

    def has_diagnostics(self) -> bool:
        return any(x.has_diagnostics() for x in self.entries.values())


@dataclasses.dataclass
class Field:
    id: int
    name: str
    type: str

    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        return bool(self.diagnostics)


@dataclasses.dataclass
class Attribute:
    id: int
    name: str
    type: str
    read_privilege: str = ""
    write_privilege: str = ""

    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        return bool(self.diagnostics)


@dataclasses.dataclass
class Command:
    id: int
    name: str
    parameters: dict[int, Field]
    privilege: str = ""

    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        if any(x.has_diagnostics() for x in self.parameters.values()):
            return True
        return bool(self.diagnostics)


@dataclasses.dataclass
class Event:
    id: int
    name: str
    parameters: dict[int, Field]

    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        if any(x.has_diagnostics() for x in self.parameters.values()):
            return True
        return bool(self.diagnostics)


@dataclasses.dataclass
class Cluster:
    ns: str
    id: int
    name: str
    attributes: dict[int, Attribute]
    commands_in: dict[int, Command]
    commands_out: dict[int, Command]
    events: dict[int, Event]

    # Collection of enums used by this cluster.
    enums: dict[str, Enum] = dataclasses.field(default_factory=dict)
    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)
    # Keep track of spec versions where this cluster appears.
    versions: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        if any(x.has_diagnostics() for x in self.attributes.values()):
            return True
        if any(x.has_diagnostics() for x in self.commands_in.values()):
            return True
        if any(x.has_diagnostics() for x in self.commands_out.values()):
            return True
        if any(x.has_diagnostics() for x in self.events.values()):
            return True
        if any(x.has_diagnostics() for x in self.enums.values()):
            return True
        return bool(self.diagnostics)


@dataclasses.dataclass
class Struct:
    name: str
    fields: list[Field]

    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        if any(x.has_diagnostics() for x in self.fields):
            return True
        return bool(self.diagnostics)


@dataclasses.dataclass
class Device:
    id: int
    name: str
    clusters: set[int]

    # Keep track of any name changes across versions for diagnostics.
    diagnostics: list[str] = dataclasses.field(default_factory=list)

    def has_diagnostics(self) -> bool:
        return bool(self.diagnostics)


def diag_name_changed(spec: str, old: str, new: str):
    return f"Name change in spec v{spec}: '{old}' -> '{new}'"


def collect_clusters(data_model_dir: Path, versions: list[str]):
    """Collect clusters with attributes/commands/events across ALL versions."""
    clusters: dict[int, Cluster] = {}
    for ver in versions:
        for xml_file in _xml_files_for_version(data_model_dir, ver):
            ns = _xml_file_to_namespace(xml_file)
            tree = ET.parse(xml_file)

            # Get all enum types defined in this XML namespace.
            enums: dict[str, Enum] = {}
            for enum_elem in tree.xpath("./dataTypes/enum[@name]"):
                name = enum_elem.get("name")
                enum_new = Enum(name=ns + name, entries={})
                enum = enums.setdefault(name, enum_new)
                for item_elem in enum_elem.xpath("./item[@value]"):
                    id = int(item_elem.get("value"), 0)
                    name = _to_pascal(item_elem.get("name", ""))
                    entry_new = EnumItem(id=id, name=name)
                    entry = enum.entries.setdefault(id, entry_new)
                    if entry.name != name:
                        entry.diagnostics.append(diag_name_changed(ver, entry.name, name))
                        # Update to the latest name.
                        entry.name = _to_pascal(name)

            def _get_type(element: ET._Element) -> str:
                """Extract the type from an XML element, handling any special cases."""
                type = element.get("type", "")
                if (
                    type.lower() == "list"
                    # For list types, we need to get the element type from the "entry" element.
                    and (entry := element.find("./entry")) is not None
                    and (entry_type := _get_type(entry))
                ):
                    return f"List<{entry_type}>"
                if enum := enums.get(type):
                    return enum.name
                return _get_type_name(type)

            # Find all cluster ID entries within the clusterIds container.
            for cluster_elem in tree.xpath("./clusterIds/clusterId[@id]"):
                id = int(cluster_elem.get("id"), 0)
                name = cluster_elem.get("name", "")
                cluster_new = Cluster(
                    ns=ns,
                    id=id,
                    name=name,
                    attributes={},
                    commands_in={},
                    commands_out={},
                    events={},
                )
                cluster = clusters.setdefault(id, cluster_new)
                cluster.enums = enums
                cluster.versions.append(ver)
                if cluster.name != name:
                    cluster.diagnostics.append(diag_name_changed(ver, cluster.name, name))
                    # Update to the latest name.
                    cluster.name = name

                # Collect cluster's attributes.
                for attr_elem in cluster_elem.xpath("../../attributes/attribute[@id]"):
                    id = int(attr_elem.get("id"), 0)
                    name = attr_elem.get("name", "")
                    attr_new = Attribute(id=id, name=name, type=_get_type(attr_elem))
                    attr = cluster.attributes.setdefault(id, attr_new)
                    if attr.name != name:
                        attr.diagnostics.append(diag_name_changed(ver, attr.name, name))
                        # Update to the latest name.
                        attr.name = name
                    if priv := attr_elem.xpath("./access/@readPrivilege"):
                        attr.read_privilege = priv[0]
                    if priv := attr_elem.xpath("./access/@writePrivilege"):
                        attr.write_privilege = priv[0]

                # Collect cluster's commands.
                for cmd_elem in cluster_elem.xpath("../../commands/command[@id]"):
                    id = int(cmd_elem.get("id"), 0)
                    name = cmd_elem.get("name", "")
                    cmd_new = Command(id=id, name=name, parameters={})
                    if cmd_elem.get("direction") == "commandToServer":
                        cmd = cluster.commands_in.setdefault(id, cmd_new)
                    else:
                        cmd = cluster.commands_out.setdefault(id, cmd_new)
                    if cmd.name != name:
                        cmd.diagnostics.append(diag_name_changed(ver, cmd.name, name))
                        # Update to the latest name.
                        cmd.name = name
                    if priv := cmd_elem.xpath("./access/@invokePrivilege"):
                        cmd.privilege = priv[0]
                    # Collect command's parameters.
                    for field in cmd_elem.xpath("./field"):
                        id = int(field.get("id", "0"), 0)
                        name = field.get("name", "")
                        param_new = Field(id=id, name=name, type=_get_type(field))
                        param = cmd.parameters.setdefault(id, param_new)
                        if param.name != name:
                            param.diagnostics.append(diag_name_changed(ver, param.name, name))
                            # Update to the latest name.
                            param.name = name

                # Collect cluster's events.
                for event_elem in cluster_elem.xpath("../../events/event[@id]"):
                    id = int(event_elem.get("id"), 0)
                    name = event_elem.get("name", "")
                    event_new = Event(id=id, name=name, parameters={})
                    event = cluster.events.setdefault(id, event_new)
                    if event.name != name:
                        event.diagnostics.append(diag_name_changed(ver, event.name, name))
                        # Update to the latest name.
                        event.name = name
                    # Collect event's parameters.
                    for field in event_elem.xpath("./field"):
                        id = int(field.get("id", "0"), 0)
                        name = field.get("name", "")
                        param_new = Field(id=id, name=name, type=_get_type(field))
                        param = event.parameters.setdefault(id, param_new)
                        if param.name != name:
                            param.diagnostics.append(diag_name_changed(ver, param.name, name))
                            # Update to the latest name.
                            param.name = name

    # Print diagnostics for any name changes across versions.
    for cluster in sorted(clusters.values(), key=lambda x: x.id):
        if not cluster.has_diagnostics():
            continue
        print(f"Cluster 0x{cluster.id:04X}:")
        for diag in cluster.diagnostics:
            print(f"  {diag}")
        for enum in sorted(cluster.enums.values(), key=lambda x: x.name):
            if not enum.has_diagnostics():
                continue
            print(f"Enum {enum.name}:")
            for item in sorted(enum.entries.values(), key=lambda x: x.id):
                if not item.has_diagnostics():
                    continue
                for diag in item.diagnostics:
                    print(f"  {diag}")
        for attr in sorted(cluster.attributes.values(), key=lambda x: x.id):
            if not attr.has_diagnostics():
                continue
            print(f"  Attribute 0x{attr.id:04X}:")
            for diag in attr.diagnostics:
                print(f"    {diag}")
        for cmd in sorted(cluster.commands_in.values(), key=lambda x: x.id):
            if not cmd.has_diagnostics():
                continue
            print(f"  Command (to server) 0x{cmd.id:04X}:")
            for diag in cmd.diagnostics:
                print(f"    {diag}")
            for param in sorted(cmd.parameters.values(), key=lambda x: x.id):
                if not param.has_diagnostics():
                    continue
                print(f"    Parameter 0x{param.id:04X}:")
                for diag in param.diagnostics:
                    print(f"      {diag}")
        for cmd in sorted(cluster.commands_out.values(), key=lambda x: x.id):
            if not cmd.has_diagnostics():
                continue
            print(f"  Command (to client) 0x{cmd.id:04X}:")
            for diag in cmd.diagnostics:
                print(f"    {diag}")
            for param in sorted(cmd.parameters.values(), key=lambda x: x.id):
                if not param.has_diagnostics():
                    continue
                print(f"    Parameter 0x{param.id:04X}:")
                for diag in param.diagnostics:
                    print(f"      {diag}")
        for event in sorted(cluster.events.values(), key=lambda x: x.id):
            if not event.has_diagnostics():
                continue
            print(f"  Event 0x{event.id:04X}:")
            for diag in event.diagnostics:
                print(f"    {diag}")
            for param in sorted(event.parameters.values(), key=lambda x: x.id):
                if not param.has_diagnostics():
                    continue
                print(f"    Parameter 0x{param.id:04X}:")
                for diag in param.diagnostics:
                    print(f"      {diag}")

    return clusters


def collect_devices(data_model_dir: Path, versions: list[str]):
    """Collect devices across ALL versions."""
    devices: dict[int, Device] = {}
    for ver in versions:
        for xml_file in _xml_files_for_version(data_model_dir, ver):
            tree = ET.parse(xml_file)
            for elem in tree.xpath("//deviceType[@id]"):
                id = int(elem.get("id"), 0)
                name = elem.get("name", "")
                device_new = Device(id=id, name=name, clusters=set())
                device = devices.setdefault(id, device_new)
                if device.name != name:
                    device.diagnostics.append(diag_name_changed(ver, device.name, name))
                    # Update to the latest name.
                    device.name = name
                device.clusters.update(int(id, 0) for id in elem.xpath("./clusters/cluster/@id"))
    # Print diagnostics for any name changes across versions.
    for device in sorted(devices.values(), key=lambda x: x.id):
        if not device.has_diagnostics():
            continue
        print(f"Device 0x{device.id:04X}:")
        for diag in device.diagnostics:
            print(f"  {diag}")
    return devices


parser = argparse.ArgumentParser(
    description=(
        "Parse Matter data-model XML files and emit Kotlin code with Matter "
        "registry classes - device types, clusters, attributes, commands, data "
        "types, privileges, etc. The generated code is used at runtime to map "
        "between numeric IDs and human-readable names."
    ),
)
parser.add_argument(
    "--out-dir",
    metavar="DIR",
    type=Path,
    default="app/src/main/java/io/aether/android/matter",
    help="directory to write Matter registry Kotlin files; default: %(default)s",
)
parser.add_argument(
    "DATA_MODEL_DIR",
    type=Path,
    help=(
        "top-level Matter data-model directory that contains one "
        "sub-directory per spec version (e.g. matter-sdk/data_model)"
    ),
)

args = parser.parse_args()

versions = []
# Discover version sub-directories (e.g. 1.0, 1.1, 1.2, 1.2.1, etc.)
for entry in args.DATA_MODEL_DIR.iterdir():
    if entry.is_dir() and re.match(r"^\d+\.\d+", entry.name):
        versions.append(entry.name)

if not versions:
    print(f"ERROR: No version sub-directories found in {args.DATA_MODEL_DIR}", file=sys.stderr)
    sys.exit(1)

versions.sort(key=lambda v: [int(x) for x in v.split(".")])
print(f"Found {len(versions)} version(s): {', '.join(versions)}")

print("Collecting clusters across all versions...")
clusters = collect_clusters(args.DATA_MODEL_DIR, versions)
print("Collecting device types across all versions...")
devices = collect_devices(args.DATA_MODEL_DIR, versions)

enums: dict[str, Enum] = {}
for cluster in clusters.values():
    for enum in cluster.enums.values():
        enums[enum.name] = enum

types: set[str] = set()
for cluster in clusters.values():
    for attr in cluster.attributes.values():
        types.add(attr.type)
    for cmd in cluster.commands_in.values():
        for param in cmd.parameters.values():
            types.add(param.type)
    for cmd in cluster.commands_out.values():
        for param in cmd.parameters.values():
            types.add(param.type)
    for event in cluster.events.values():
        for param in event.parameters.values():
            types.add(param.type)

spdx = "SPDX"  # Hide code generator from REUSE tool.
HEADER = f"""
// {spdx}-FileCopyrightText: 2026 The Authors
// {spdx}-License-Identifier: Apache-2.0
//
// Auto-generated by matter-data-model-parser.py - do not edit.

package io.aether.android.matter

""".lstrip()

print("Writing clusters...")
with open(args.out_dir / "Clusters.kt", "w") as f:
    f.write(HEADER)
    f.write("object Clusters {\n")
    for cluster in sorted(clusters.values(), key=lambda x: x.id):
        f.write(f"  object {_to_pascal(cluster.name)} {{\n")
        f.write(f"    val ID = ClusterId(0x{cluster.id:04X}u)\n")
        f.write("    object Attributes {\n")
        for attr in sorted(cluster.attributes.values(), key=lambda x: x.id):
            f.write(f"      object {_to_pascal(attr.name)} {{\n")
            f.write(f"        val ID = AttributeId(0x{attr.id:04X}u)\n")
            f.write("      }\n")
        f.write("    }\n")
        f.write("    object CommandsIncoming {\n")
        for cmd in sorted(cluster.commands_in.values(), key=lambda x: x.id):
            f.write(f"      object {_to_pascal(cmd.name)} {{\n")
            f.write(f"        val ID = CommandId(0x{cmd.id:04X}u)\n")
            f.write("      }\n")
        f.write("    }\n")
        f.write("    object CommandsOutgoing {\n")
        for cmd in sorted(cluster.commands_out.values(), key=lambda x: x.id):
            f.write(f"      object {_to_pascal(cmd.name)} {{\n")
            f.write(f"        val ID = CommandId(0x{cmd.id:04X}u)\n")
            f.write("      }\n")
        f.write("    }\n")
        f.write("    object Events {\n")
        for event in sorted(cluster.events.values(), key=lambda x: x.id):
            f.write(f"      object {_to_pascal(event.name)} {{\n")
            f.write(f"        val ID = EventId(0x{event.id:04X}u)\n")
            f.write("      }\n")
        f.write("    }\n")
        f.write("  }\n")
    f.write("}\n")


print("Writing enums...")
with open(args.out_dir / "Enums.kt", "w") as f:
    f.write(HEADER)
    f.write("object Enums {\n")
    for enum in sorted(enums.values(), key=lambda x: x.name):
        name = _to_pascal(enum.name.removesuffix("Enum"))
        f.write(f"  enum class {name}(val value: Int) {{\n")
        for item in sorted(enum.entries.values(), key=lambda x: x.id):
            name = _to_pascal(item.name)
            if name[0].isdigit():
                name = f"`{name}`"
            f.write(f"    {name}({item.id}),\n")
        f.write("  }\n\n")
    f.write("}\n")


print("Writing data types and privileges...")
with open(args.out_dir / "DataTypes.kt", "w") as f:
    f.write(HEADER)
    f.write("enum class DataType(val label: String) {\n")
    f.write('  UNKNOWN("UNKNOWN"),\n')
    for type in sorted(filter(None, types)):
        f.write(f'  {_to_upper_snake(type)}("{type}"),\n')
    f.write("}\n")


print("Writing devices...")
with open(args.out_dir / "Devices.kt", "w") as f:
    f.write(HEADER)
    f.write("object Devices {\n")
    for id, device in sorted(devices.items()):
        f.write(f"  object {_to_pascal(device.name)} {{\n")
        f.write(f"    val ID = DeviceTypeId(0x{id:04X}u)\n")
        f.write("  }\n\n")
    f.write("}\n")
    f.write("\n")
    f.write("val DEVICES =\n")
    f.write("    mapOf<DeviceTypeId, String>(\n")
    for id, device in sorted(devices.items()):
        f.write(f'        Devices.{_to_pascal(device.name)}.ID to "{device.name}",\n')
    f.write("    )\n")


def write_cluster_registry(clusters: dict[int, Cluster], path: Path, version: str):
    """Write data model registry with given clusters."""
    with open(path, "w") as f:
        f.write(HEADER)
        f.write(f"val CLUSTERS_{version.replace('.', '_')} =\n")
        f.write("    mapOf<ClusterId, ClusterInfo>(\n")
        for cluster in sorted(clusters.values(), key=lambda x: x.id):
            cluster_namespace = f"Clusters.{_to_pascal(cluster.name)}"
            f.write(f"    {cluster_namespace}.ID to ClusterInfo(\n")
            f.write(f'      name = "{cluster.name}",\n')
            f.write("      attributes = mapOf<AttributeId, AttributeInfo>(\n")
            for attr in sorted(cluster.attributes.values(), key=lambda x: x.id):
                attr_namespace = f"{cluster_namespace}.Attributes.{_to_pascal(attr.name)}"
                f.write(f"        {attr_namespace}.ID to AttributeInfo(\n")
                f.write(f'          name = "{attr.name}",\n')
                f.write(f"          type = {_get_type_enum(attr.type)},\n")
                if attr.read_privilege:
                    priv = _get_privilege_enum(attr.read_privilege)
                    f.write(f"          readPrivilege = {priv},\n")
                if attr.write_privilege:
                    priv = _get_privilege_enum(attr.write_privilege)
                    f.write(f"          writePrivilege = {priv},\n")
                f.write("        ),\n")
            f.write("      ),\n")
            f.write("      commandsIncoming = mapOf<CommandId, CommandInfo>(\n")
            for cmd in sorted(cluster.commands_in.values(), key=lambda x: x.id):
                cmd_namespace = f"{cluster_namespace}.CommandsIncoming.{_to_pascal(cmd.name)}"
                f.write(f"        {cmd_namespace}.ID to CommandInfo(\n")
                f.write(f'          name = "{cmd.name}",\n')
                if cmd.privilege:
                    f.write(f"          privilege = {_get_privilege_enum(cmd.privilege)},\n")
                f.write("          parameters = mapOf<UInt, ParameterInfo>(\n")
                for param in sorted(cmd.parameters.values(), key=lambda x: x.id):
                    f.write(f"            {param.id}u to ParameterInfo(\n")
                    f.write(f'              name = "{param.name}",\n')
                    f.write(f"              type = {_get_type_enum(param.type)},\n")
                    f.write("            ),\n")
                f.write("          ),\n")
                f.write("        ),\n")
            f.write("      ),\n")
            f.write("      commandsOutgoing = mapOf<CommandId, CommandInfo>(\n")
            for cmd in sorted(cluster.commands_out.values(), key=lambda x: x.id):
                cmd_namespace = f"{cluster_namespace}.CommandsOutgoing.{_to_pascal(cmd.name)}"
                f.write(f"        {cmd_namespace}.ID to CommandInfo(\n")
                f.write(f'          name = "{cmd.name}",\n')
                if cmd.privilege:
                    f.write(f"          privilege = {_get_privilege_enum(cmd.privilege)},\n")
                f.write("          parameters = mapOf<UInt, ParameterInfo>(\n")
                for param in sorted(cmd.parameters.values(), key=lambda x: x.id):
                    f.write(f"            {param.id}u to ParameterInfo(\n")
                    f.write(f'              name = "{param.name}",\n')
                    f.write(f"              type = {_get_type_enum(param.type)},\n")
                    f.write("            ),\n")
                f.write("          ),\n")
                f.write("        ),\n")
            f.write("      ),\n")
            f.write("      events = mapOf<EventId, EventInfo>(\n")
            for event in sorted(cluster.events.values(), key=lambda x: x.id):
                event_namespace = f"{cluster_namespace}.Events.{_to_pascal(event.name)}"
                f.write(f"        {event_namespace}.ID to EventInfo(\n")
                f.write(f'          name = "{event.name}",\n')
                f.write("        ),\n")
            f.write("      ),\n")
            f.write("    ),\n")
        f.write(")\n")


written = []
for ver in versions:
    subset = {}
    for cluster in tuple(clusters.values()):
        if ver in cluster.versions:
            subset[cluster.id] = clusters.pop(cluster.id)
    if not subset:
        continue
    print(f"Writing data model registry for version {ver}...")
    path = args.out_dir / f"ClusterRegistryV{ver}.kt"
    write_cluster_registry(subset, path, ver)
    written.append(ver)

print("Writing global data model registry...")
with open(args.out_dir / "ClusterRegistry.kt", "w") as f:
    f.write(HEADER)
    f.write("val CLUSTERS =\n")
    f.write("    " + " + ".join([f"CLUSTERS_{ver.replace('.', '_')}" for ver in written]))
    f.write("\n")
