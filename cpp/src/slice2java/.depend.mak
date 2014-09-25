
Gen.obj: \
	Gen.cpp \
    "$(includedir)\IceUtil\DisableWarnings.h" \
    "..\..\src\slice2java\Gen.h" \
    "$(includedir)\Slice\Parser.h" \
    "$(includedir)\IceUtil\Shared.h" \
    "$(includedir)\IceUtil\Config.h" \
    "$(includedir)\IceUtil\Handle.h" \
    "$(includedir)\IceUtil\Exception.h" \
    "$(includedir)\Slice\JavaUtil.h" \
    "$(includedir)\IceUtil\OutputUtil.h" \
    "$(includedir)\Slice\Checksum.h" \
    "$(includedir)\Slice\Util.h" \
    "$(includedir)\IceUtil\Functional.h" \
    "$(includedir)\IceUtil\Iterator.h" \
    "$(includedir)\IceUtil\StringUtil.h" \
    "$(includedir)\IceUtil\InputUtil.h" \

Main.obj: \
	Main.cpp \
    "$(includedir)\IceUtil\Options.h" \
    "$(includedir)\IceUtil\Config.h" \
    "$(includedir)\IceUtil\RecMutex.h" \
    "$(includedir)\IceUtil\Lock.h" \
    "$(includedir)\IceUtil\ThreadException.h" \
    "$(includedir)\IceUtil\Exception.h" \
    "$(includedir)\IceUtil\Time.h" \
    "$(includedir)\IceUtil\MutexProtocol.h" \
    "$(includedir)\IceUtil\Shared.h" \
    "$(includedir)\IceUtil\Handle.h" \
    "$(includedir)\IceUtil\CtrlCHandler.h" \
    "$(includedir)\IceUtil\Mutex.h" \
    "$(includedir)\IceUtil\MutexPtrLock.h" \
    "$(includedir)\Slice\Preprocessor.h" \
    "$(includedir)\Slice\FileTracker.h" \
    "$(includedir)\Slice\Parser.h" \
    "$(includedir)\Slice\Util.h" \
    "$(includedir)\IceUtil\OutputUtil.h" \
    "..\..\src\slice2java\Gen.h" \
    "$(includedir)\Slice\JavaUtil.h" \
    "$(includedir)\Slice\Checksum.h" \