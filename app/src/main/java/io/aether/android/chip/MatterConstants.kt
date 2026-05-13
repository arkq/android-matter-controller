// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

object MatterConstants {
  val DeviceTypesMap =
      mapOf<Long, String>(
          0x00AL to "Door Lock",
          0x00BL to "Door Lock Controller",
          0x00EL to "Aggregator",
          0x00FL to "Generic Switch",
          0x011L to "Power Source",
          0x012L to "OTA Requestor",
          0x013L to "Bridged Node",
          0x014L to "OTA Provider",
          0x015L to "Contact Sensor",
          0x016L to "Root Node",
          0x017L to "Solar Power",
          0x018L to "Battery Storage",
          0x019L to "Secondary Network Interface",
          0x022L to "Speaker",
          0x023L to "Casting Video Player",
          0x024L to "Content App",
          0x027L to "Mode Select",
          0x028L to "Basic Video Player",
          0x029L to "Casting Video Client",
          0x02AL to "Video Remote Control",
          0x02BL to "Fan",
          0x02CL to "Air Quality Sensor",
          0x02DL to "Air Purifier",
          0x041L to "Water Freeze Detector",
          0x042L to "Water Valve",
          0x043L to "Water Leak Detector",
          0x044L to "Rain Sensor",
          0x045L to "Soil Sensor",
          0x070L to "Refrigerator",
          0x071L to "Temperature Controlled Cabinet",
          0x072L to "Room Air Conditioner",
          0x073L to "Laundry Washer",
          0x074L to "Robotic Vacuum Cleaner",
          0x075L to "Dishwasher",
          0x076L to "Smoke CO Alarm",
          0x077L to "Cook Surface",
          0x078L to "Cooktop",
          0x079L to "Microwave Oven",
          0x07AL to "Extractor Hood",
          0x07BL to "Oven",
          0x07CL to "Laundry Dryer",
          0x090L to "Network Infrastructure Manager",
          0x091L to "Thread Border Router",
          0x100L to "On/Off Light",
          0x101L to "Dimmable Light",
          0x103L to "On/Off Light Switch",
          0x104L to "Dimmer Switch",
          0x105L to "Color Dimmer Switch",
          0x106L to "Light Sensor",
          0x107L to "Occupancy Sensor",
          0x10AL to "On/Off Plug-in Unit",
          0x10BL to "Dimmable Plug-in Unit",
          0x10CL to "Color Temperature Light",
          0x10DL to "Extended Color Light",
          0x10FL to "Mounted On/Off Control",
          0x110L to "Mounted Dimmable Load Control",
          0x130L to "Joint Fabric Administrator",
          0x140L to "Intercom",
          0x141L to "Audio Doorbell",
          0x142L to "Camera",
          0x143L to "Video Doorbell",
          0x144L to "Floodlight Camera",
          0x145L to "Snapshot Camera",
          0x146L to "Chime",
          0x147L to "Camera Controller",
          0x148L to "Doorbell",
          0x150L to "Ambient Context Sensor",
          0x152L to "Proximity Ranger",
          0x202L to "Window Covering",
          0x203L to "Window Covering Controller",
          0x230L to "Closure",
          0x231L to "Closure Panel",
          0x23EL to "Closure Controller",
          0x301L to "Thermostat",
          0x302L to "Temperature Sensor",
          0x303L to "Pump",
          0x304L to "Pump Controller",
          0x305L to "Pressure Sensor",
          0x306L to "Flow Sensor",
          0x307L to "Humidity Sensor",
          0x309L to "Heat Pump",
          0x30AL to "Thermostat Controller",
          0x50CL to "EVSE",
          0x50DL to "Device Energy Management",
          0x50FL to "Water Heater",
          0x510L to "Electrical Sensor",
          0x511L to "Electrical Utility Meter",
          0x512L to "Meter Reference Point",
          0x513L to "Electrical Energy Tariff",
          0x514L to "Electrical Meter",
          0x840L to "Control Bridge",
          0x850L to "On/Off Sensor",
      )

  val ClustersMap =
      mapOf<Long, String>(
          0x003L to "Identify",
          0x004L to "Groups",
          0x005L to "Scenes",
          0x006L to "On/Off",
          0x008L to "Level Control",
          0x01CL to "Pulse Width Modulation",
          0x01DL to "Descriptor",
          0x01EL to "Binding",
          0x01FL to "Access Control",
          0x025L to "Actions",
          0x028L to "Basic Information",
          0x029L to "OTA Software Update Provider",
          0x02AL to "OTA Software Update Requestor",
          0x02BL to "Localization Configuration",
          0x02CL to "Time Format Localization",
          0x02DL to "Unit Localization",
          0x02EL to "Power Source Configuration",
          0x02FL to "Power Source",
          0x030L to "General Commissioning",
          0x031L to "Network Commissioning",
          0x032L to "Diagnostic Logs",
          0x033L to "General Diagnostics",
          0x034L to "Software Diagnostics",
          0x035L to "Thread Network Diagnostics",
          0x036L to "Wi-Fi Network Diagnostics",
          0x037L to "Ethernet Network Diagnostics",
          0x038L to "Time Synchronization",
          0x039L to "Bridged Device Basic Information",
          0x03BL to "Switch",
          0x03CL to "Administrator Commissioning",
          0x03EL to "Node Operational Credentials",
          0x03FL to "Group Key Management",
          0x040L to "Fixed Label",
          0x041L to "User Label",
          0x042L to "Proxy Configuration",
          0x043L to "Proxy Discovery",
          0x044L to "Proxy Valid",
          0x045L to "Boolean State",
          0x046L to "ICD Management",
          0x047L to "Timer",
          0x048L to "Oven Cavity Operational State",
          0x049L to "Oven Mode",
          0x04AL to "Laundry Dryer Controls",
          0x050L to "Mode Select",
          0x051L to "Laundry Washer Mode",
          0x052L to "Refrigerator And Temperature Controlled Cabinet Mode",
          0x053L to "Laundry Washer Controls",
          0x054L to "RVC Run Mode",
          0x055L to "RVC Clean Mode",
          0x056L to "Temperature Control",
          0x057L to "Refrigerator Alarm",
          0x059L to "Dishwasher Mode",
          0x05BL to "Air Quality",
          0x05CL to "Smoke CO Alarm",
          0x05DL to "Dishwasher Alarm",
          0x05EL to "Microwave Oven Mode",
          0x05FL to "Microwave Oven Control",
          0x060L to "Operational State",
          0x061L to "RVC Operational State",
          0x062L to "Scenes Management",
          0x065L to "Groupcast",
          0x071L to "HEPA Filter Monitoring",
          0x072L to "Activated Carbon Filter Monitoring",
          0x079L to "Water Tank Level Monitoring",
          0x080L to "Boolean State Configuration",
          0x081L to "Valve Configuration And Control",
          0x090L to "Electrical Power Measurement",
          0x091L to "Electrical Energy Measurement",
          0x094L to "Water Heater Management",
          0x095L to "Commodity Price",
          0x097L to "Messages",
          0x098L to "Device Energy Management",
          0x099L to "Energy EVSE",
          0x09BL to "Energy Preference",
          0x09CL to "Power Topology",
          0x09DL to "Energy EVSE Mode",
          0x09EL to "Water Heater Mode",
          0x09FL to "Device Energy Management Mode",
          0x0A0L to "Electrical Grid Conditions",
          0x101L to "Door Lock",
          0x102L to "Window Covering",
          0x104L to "Closure Control",
          0x105L to "Closure Dimension",
          0x150L to "Service Area",
          0x200L to "Pump Configuration And Control",
          0x201L to "Thermostat",
          0x202L to "Fan Control",
          0x204L to "Thermostat User Interface Configuration",
          0x205L to "Humidistat",
          0x300L to "Color Control",
          0x301L to "Ballast Configuration",
          0x400L to "Illuminance Measurement",
          0x402L to "Temperature Measurement",
          0x403L to "Pressure Measurement",
          0x404L to "Flow Measurement",
          0x405L to "Relative Humidity Measurement",
          0x406L to "Occupancy Sensing",
          0x40CL to "Carbon Monoxide Concentration Measurement",
          0x40DL to "Carbon Dioxide Concentration Measurement",
          0x413L to "Nitrogen Dioxide Concentration Measurement",
          0x415L to "Ozone Concentration Measurement",
          0x42AL to "PM2.5 Concentration Measurement",
          0x42BL to "Formaldehyde Concentration Measurement",
          0x42CL to "PM1 Concentration Measurement",
          0x42DL to "PM10 Concentration Measurement",
          0x42EL to "Total Volatile Organic Compounds Concentration Measurement",
          0x42FL to "Radon Concentration Measurement",
          0x430L to "Soil Measurement",
          0x431L to "Ambient Context Sensing",
          0x433L to "Proximity Ranging",
          0x450L to "Network Identity Management",
          0x451L to "Wi-Fi Network Management",
          0x452L to "Thread Border Router Management",
          0x453L to "Thread Network Directory",
          0x503L to "Wake On Lan",
          0x504L to "Channel",
          0x505L to "Target Navigator",
          0x506L to "Media Playback",
          0x507L to "Media Input",
          0x508L to "Low Power",
          0x509L to "Keypad Input",
          0x50AL to "Content Launcher",
          0x50BL to "Audio Output",
          0x50CL to "Application Launcher",
          0x50DL to "Application Basic",
          0x50EL to "Account Login",
          0x50FL to "Content Control",
          0x510L to "Content App Observer",
          0x550L to "Zone Management",
          0x551L to "Camera AV Stream Management",
          0x552L to "Camera AV Settings User Level Management",
          0x553L to "WebRTC Transport Provider",
          0x554L to "WebRTC Transport Requestor",
          0x555L to "Push AV Stream Transport",
          0x556L to "Chime",
          0x700L to "Commodity Tariff",
          0x750L to "Ecosystem Information",
          0x751L to "Commissioner Control",
          0x752L to "Joint Fabric Datastore",
          0x753L to "Joint Fabric Administrator",
          0x801L to "TLS Certificate Management",
          0x802L to "TLS Client Management",
          0xB06L to "Meter Identification",
          0xB07L to "Commodity Metering",
          0xFFF1FC05L to "Unit Testing",
          0xFFF1FC06L to "Fault Injection",
          0xFFF1FC20L to "Sample MEI",
      )

  // Well known cluster attributes
  const val OnOffClusterId = 0x0006L
  const val LevelControlClusterId = 0x0008L
  const val ColorControlClusterId = 0x0300L

  data class ClusterAttribute(val clusterId: Long, val attributeId: Long)

  val OnOffAttribute = ClusterAttribute(OnOffClusterId, 0x0000L)
  val LevelAttribute = ClusterAttribute(LevelControlClusterId, 0x0000L)
  val ColorTemperatureAttribute = ClusterAttribute(ColorControlClusterId, 0x0007L)

  data class ExplorerKnownAttribute(
      val id: Long,
      val name: String,
      val writable: Boolean = false,
  )

  data class ExplorerKnownCommandArgument(
      val key: String,
      val name: String,
      val minValue: Int? = null,
      val maxValue: Int? = null,
  )

  data class ExplorerKnownCommand(
      val id: Long,
      val name: String,
      val arguments: List<ExplorerKnownCommandArgument> = emptyList(),
  )

  data class ExplorerKnownEvent(val id: Long, val name: String)

  data class ExplorerKnownCluster(
      val clusterId: Long,
      val attributes: List<ExplorerKnownAttribute> = emptyList(),
      val commands: List<ExplorerKnownCommand> = emptyList(),
      val events: List<ExplorerKnownEvent> = emptyList(),
  )

  const val GlobalAttributeRangeStart = 0xFFF0L
  const val GlobalAttributeRangeEnd = 0xFFFFL

  val ExplorerGlobalAttributesById: Map<Long, ExplorerKnownAttribute> =
      listOf(
              ExplorerKnownAttribute(
                  id = 0xFFF8L,
                  name = "GeneratedCommandList",
              ),
              ExplorerKnownAttribute(
                  id = 0xFFF9L,
                  name = "AcceptedCommandList",
              ),
              ExplorerKnownAttribute(
                  id = 0xFFFAL,
                  name = "EventList",
              ),
              ExplorerKnownAttribute(
                  id = 0xFFFBL,
                  name = "AttributeList",
              ),
              ExplorerKnownAttribute(
                  id = 0xFFFCL,
                  name = "FeatureMap",
              ),
              ExplorerKnownAttribute(
                  id = 0xFFFDL,
                  name = "ClusterRevision",
              ),
          )
          .associateBy { it.id }

  fun isGlobalAttributeId(attributeId: Long): Boolean {
    return attributeId in GlobalAttributeRangeStart..GlobalAttributeRangeEnd
  }

  val ExplorerKnownClustersById =
      listOf(
              ExplorerKnownCluster(
                  clusterId = 0x0028L,
                  attributes =
                      listOf(
                          ExplorerKnownAttribute(
                              id = 0x0005L,
                              name = "NodeLabel",
                              writable = true,
                          )
                      ),
              ),
              ExplorerKnownCluster(
                  clusterId = 0x0006L,
                  attributes =
                      listOf(
                          ExplorerKnownAttribute(
                              id = 0x0000L,
                              name = "OnOff",
                          )
                      ),
                  commands =
                      listOf(
                          ExplorerKnownCommand(
                              id = 0x0000L,
                              name = "Off",
                          ),
                          ExplorerKnownCommand(
                              id = 0x0001L,
                              name = "On",
                          ),
                          ExplorerKnownCommand(
                              id = 0x0002L,
                              name = "Toggle",
                          ),
                      ),
              ),
              ExplorerKnownCluster(
                  clusterId = 0x0008L,
                  attributes =
                      listOf(
                          ExplorerKnownAttribute(
                              id = 0x0000L,
                              name = "CurrentLevel",
                          )
                      ),
                  commands =
                      listOf(
                          ExplorerKnownCommand(
                              id = 0x0000L,
                              name = "MoveToLevel",
                              arguments =
                                  listOf(
                                      ExplorerKnownCommandArgument(
                                          key = "level",
                                          name = "Level",
                                          minValue = 0,
                                          maxValue = 254,
                                      ),
                                      ExplorerKnownCommandArgument(
                                          key = "transitionTime",
                                          name = "Transition Time",
                                          minValue = 0,
                                          maxValue = 65535,
                                      ),
                                  ),
                          )
                      ),
              ),
          )
          .associateBy { it.clusterId }
}
