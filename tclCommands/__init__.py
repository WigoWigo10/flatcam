import importlib
import pkgutil
import sys

# allowed command modules (please append them alphabetically ordered)
import tclCommands.TclCommandAddCircle
import tclCommands.TclCommandAddPolygon
import tclCommands.TclCommandAddPolyline
import tclCommands.TclCommandAddRectangle
import tclCommands.TclCommandAlignDrill
import tclCommands.TclCommandAlignDrillGrid
import tclCommands.TclCommandBbox
import tclCommands.TclCommandBounds
import tclCommands.TclCommandClearShell
import tclCommands.TclCommandCncjob
import tclCommands.TclCommandCopperClear
import tclCommands.TclCommandCutout
import tclCommands.TclCommandDelete
import tclCommands.TclCommandDrillcncjob
import tclCommands.TclCommandExportDXF
import tclCommands.TclCommandExportExcellon
import tclCommands.TclCommandExportGerber
import tclCommands.TclCommandExportGcode
import tclCommands.TclCommandExportSVG
import tclCommands.TclCommandExteriors
import tclCommands.TclCommandGeoCutout
import tclCommands.TclCommandGeoUnion
import tclCommands.TclCommandGetNames
import tclCommands.TclCommandGetPath
import tclCommands.TclCommandGetSys
import tclCommands.TclCommandHelp
import tclCommands.TclCommandInteriors
import tclCommands.TclCommandIsolate
import tclCommands.TclCommandFollow
import tclCommands.TclCommandJoinExcellon
import tclCommands.TclCommandJoinGeometry
import tclCommands.TclCommandListSys
import tclCommands.TclCommandMillDrills
import tclCommands.TclCommandMillSlots
import tclCommands.TclCommandMirror
import tclCommands.TclCommandNew
import tclCommands.TclCommandNregions
import tclCommands.TclCommandNewExcellon
import tclCommands.TclCommandNewGeometry
import tclCommands.TclCommandNewGerber
import tclCommands.TclCommandOffset
import tclCommands.TclCommandOpenDXF
import tclCommands.TclCommandOpenExcellon
import tclCommands.TclCommandOpenGCode
import tclCommands.TclCommandOpenGerber
import tclCommands.TclCommandOpenProject
import tclCommands.TclCommandOpenSVG
import tclCommands.TclCommandOptions
import tclCommands.TclCommandPaint
import tclCommands.TclCommandPanelize
import tclCommands.TclCommandPlotAll
import tclCommands.TclCommandPlotObjects
import tclCommands.TclCommandQuit
import tclCommands.TclCommandSaveProject
import tclCommands.TclCommandSaveSys
import tclCommands.TclCommandScale
import tclCommands.TclCommandSetActive
import tclCommands.TclCommandSetOrigin
import tclCommands.TclCommandSetPath
import tclCommands.TclCommandSetSys
import tclCommands.TclCommandSkew
import tclCommands.TclCommandSubtractPoly
import tclCommands.TclCommandSubtractRectangle
import tclCommands.TclCommandVersion
import tclCommands.TclCommandWriteGCode


__all__ = []

for _finder, name, is_pkg in pkgutil.walk_packages(__path__, prefix=__name__ + '.'):
    # NOTE (local patch): loader.find_module()/load_module() was removed in
    # Python 3.12 (PEP 451's find_spec/exec_module has been the only path
    # for years). Every command module is already explicitly imported above,
    # so this loop only needs to catch modules added without updating that
    # list, and to keep populating __all__.
    importlib.import_module(name)
    __all__.append(name.rpartition('.')[2])


def register_all_commands(app, commands):
    """
    Static method which registers all known commands.

    Command should  be for now in directory tclCommands and module should start with TCLCommand
    Class  have to follow same  name as module.

    we need import all  modules  in top section:
    import tclCommands.TclCommandExteriors
    at this stage we can include only wanted  commands  with this, auto loading may be implemented in future
    I have no enough knowledge about python's anatomy. Would be nice to include all classes which are descendant etc.

    :param app: FlatCAMApp
    :param commands: List of commands being updated
    :return: None
    """

    tcl_modules = {k: v for k, v in list(sys.modules.items()) if k.startswith('tclCommands.TclCommand')}

    for key, mod in list(tcl_modules.items()):
        if key != 'tclCommands.TclCommand':
            class_name = key.split('.')[1]
            class_type = getattr(mod, class_name)
            command_instance = class_type(app)

            for alias in command_instance.aliases:
                try:
                    description = command_instance.description
                except AttributeError:
                    description = ''
                commands[alias] = {
                    'fcn': command_instance.execute_wrapper,
                    'help': command_instance.get_decorated_help(),
                    'description': description
                }
